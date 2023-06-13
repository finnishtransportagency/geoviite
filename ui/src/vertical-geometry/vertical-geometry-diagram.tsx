import React, { MouseEvent, useEffect, useMemo, useRef, useState, WheelEvent } from 'react';
import { PublishType } from 'common/common-model';
import { useLoader, useTwoPartEffect } from 'utils/react-utils';
import { GeometryAlignmentId, GeometryPlanId, VerticalGeometryItem } from 'geometry/geometry-model';
import {
    getGeometryPlanVerticalGeometry,
    getLocationTrackLinkingSummary,
    getLocationTrackVerticalGeometry,
    getPlanAlignmentStartAndEnd,
    PlanLinkingSummaryItem,
    TrackKmHeights,
} from 'geometry/geometry-api';
import styles from './vertical-geometry-diagram.scss';
import { Translate } from 'vertical-geometry/translate';
import { TrackAddressRuler } from 'vertical-geometry/track-address-ruler';
import { HeightLabels, HeightLines } from 'vertical-geometry/height-lines';
import { PviGeometry } from 'vertical-geometry/pvi-geometry';
import { LabeledTicks } from 'vertical-geometry/labeled-ticks';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { PlanLinking } from 'vertical-geometry/plan-linking';
import { getSnappedPoint } from 'vertical-geometry/snapped-point';
import { Coordinates, xToM } from 'vertical-geometry/coordinates';
import { PointIndicator } from 'vertical-geometry/point-indicator';
import { HeightGraph } from 'vertical-geometry/height-graph';
import { HeightTooltip } from 'vertical-geometry/height-tooltip';
import useResizeObserver from 'use-resize-observer';
import { getLocationTrackStartAndEnd } from 'track-layout/layout-location-track-api';
import {
    minimumApproximateHorizontalTickWidthPx,
    minimumIntervalOrLongest,
} from 'vertical-geometry/ticks-at-intervals';
import { OnSelectOptions } from 'selection/selection-model';
import { ChangeTimes } from 'common/common-slice';
import { BoundingBox } from 'model/geometry';
import {
    findTrackMeterIndexContainingM,
    getTrackMeterPairAroundIndex,
} from 'vertical-geometry/track-meter-index';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { useAlignmentHeights } from 'vertical-geometry/km-heights-fetch';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';

const chartHeightPx = 240;
const topHeightPaddingPx = 120;
const bottomHeightPaddingPx = 0;
const fullDiagramHeightPx = 300;
const minimumPixelWidthToDrawTangentArrows = 0.05;

export type VerticalGeometryDiagramAlignmentId =
    | { planId: GeometryPlanId; alignmentId: GeometryAlignmentId }
    | { locationTrackId: LocationTrackId; publishType: PublishType };

interface VerticalGeometryDiagramProps {
    alignmentId: VerticalGeometryDiagramAlignmentId;
    onSelect: (options: OnSelectOptions) => void;
    changeTimes: ChangeTimes;
    showArea: (area: BoundingBox) => void;
}

function loadGeometry(
    changeTimes: ChangeTimes,
    alignmentId:
        | { planId: GeometryPlanId; alignmentId: GeometryAlignmentId }
        | { locationTrackId: LocationTrackId; publishType: PublishType },
): Promise<VerticalGeometryItem[] | null | undefined> {
    return 'planId' in alignmentId
        ? getGeometryPlanVerticalGeometry(changeTimes.geometryPlan, alignmentId.planId).then(
              (allPlanGeometries) =>
                  allPlanGeometries?.filter((vgl) => vgl.alignmentId == alignmentId.alignmentId),
          )
        : getLocationTrackVerticalGeometry(
              changeTimes.layoutLocationTrack,
              alignmentId.publishType,
              alignmentId.locationTrackId,
              undefined,
              undefined,
          );
}

// we don't really need the station values in the plan geometry for anything in this entire diagram
function substituteLayoutStationsForGeometryStations(
    geometryItem: VerticalGeometryItem,
): VerticalGeometryItem {
    return {
        ...geometryItem,
        start: {
            ...geometryItem.start,
            station: geometryItem.layoutStartStation ?? geometryItem.start.station,
        },
        point: {
            ...geometryItem.point,
            station: geometryItem.layoutPointStation ?? geometryItem.point.station,
        },
        end: {
            ...geometryItem.end,
            station: geometryItem.layoutEndStation ?? geometryItem.end.station,
        },
    };
}

function processLayoutGeometries(
    geometry: VerticalGeometryItem[],
    linkingSummary: PlanLinkingSummaryItem[],
) {
    const linkedAreaSourceFile = (layoutM: number) =>
        linkingSummary.find((linkingSummaryItem) => linkingSummaryItem.endM >= layoutM)?.filename;
    return geometry
        .map(substituteLayoutStationsForGeometryStations)
        .filter(
            (geom) =>
                geom.fileName === linkedAreaSourceFile(geom.start.station) ||
                geom.fileName === linkedAreaSourceFile(geom.end.station) ||
                geom.fileName === linkedAreaSourceFile(geom.point.station),
        );
}

function getBottomAndTopTicks(
    kmHeights: TrackKmHeights[],
    geometry: VerticalGeometryItem[],
): [number, number] {
    const heightsToBounds = (heights: number[]): [number, number] | undefined =>
        heights.length === 0
            ? undefined
            : [Math.floor(Math.min(...heights)), Math.ceil(Math.max(...heights))];
    // most of the time we have some visible heights on the track km; but sometimes there are holes in the linking
    // reaching across a whole track km and we're zoomed into it, in which case we'll fall back to using height bounds
    // calculated from the geometry; and sometimes there are no heights at all, in which case we don't need to worry
    // about how tall to display them anyway and can just use a (suitably strange) fallback
    return (
        heightsToBounds(
            kmHeights
                .flatMap(({ trackMeterHeights }) => trackMeterHeights.map(({ height }) => height))
                .filter(filterNotEmpty),
        ) ??
        heightsToBounds(
            geometry.flatMap((p) => [p.start.height, p.point.height, p.end.height]),
        ) ?? [0, 100]
    );
}

function getStartAndEnd(alignmentId: VerticalGeometryDiagramAlignmentId) {
    return 'planId' in alignmentId
        ? getPlanAlignmentStartAndEnd(alignmentId.planId, alignmentId.alignmentId)
        : getLocationTrackStartAndEnd(alignmentId.locationTrackId, alignmentId.publishType);
}

const VerticalGeometryDiagramSizeHolder: React.FC<VerticalGeometryDiagramProps> = ({
    alignmentId,
    changeTimes,
    ...rest
}) => {
    const ref = useRef<HTMLDivElement>(null);
    /**
     startM and endM are the endpoints of the visible parts of the diagram, in m-values (not necessarily hitting the
     m-values of actual points on the alignment)
     */
    const [startM, setStartM] = useState<number>();
    const [endM, setEndM] = useState<number>();
    const [alignmentStartM, setAlignmentStartM] = useState<number>();
    const [alignmentEndM, setAlignmentEndM] = useState<number>();
    const [oldWidth, setOldWidth] = useState<number>();
    useResizeObserver({
        ref,
        onResize: ({ width }) => {
            setOldWidth(ref.current?.clientWidth);
            if (
                width === undefined ||
                oldWidth === undefined ||
                startM === undefined ||
                endM === undefined
            ) {
                return;
            }
            const oldWidthRelativeEndM = startM + (endM - startM) * (width / oldWidth);
            setEndM(
                alignmentEndM
                    ? Math.min(oldWidthRelativeEndM, alignmentEndM)
                    : oldWidthRelativeEndM,
            );
        },
    });

    useTwoPartEffect(
        () => getStartAndEnd(alignmentId),
        (startAndEnd) => {
            const start = startAndEnd?.start?.point?.m;
            const end = startAndEnd?.end?.point?.m;
            setStartM(start);
            setAlignmentStartM(start);
            setEndM(end);
            setAlignmentEndM(end);
        },
        [alignmentId, changeTimes.layoutLocationTrack, changeTimes.geometryPlan],
    );

    return (
        <div ref={ref}>
            {ref.current &&
                startM !== undefined &&
                endM !== undefined &&
                alignmentStartM !== undefined &&
                alignmentEndM !== undefined && (
                    <VerticalGeometryDiagram
                        diagramWidthPx={ref.current.clientWidth}
                        alignmentId={alignmentId}
                        startM={startM}
                        setStartM={setStartM}
                        endM={endM}
                        setEndM={setEndM}
                        alignmentStartM={alignmentStartM}
                        alignmentEndM={alignmentEndM}
                        changeTimes={changeTimes}
                        {...rest}
                    />
                )}
        </div>
    );
};

const VerticalGeometryDiagram: React.FC<{
    alignmentId: VerticalGeometryDiagramAlignmentId;
    diagramWidthPx: number;
    startM: number;
    endM: number;
    setStartM: React.Dispatch<React.SetStateAction<number>>;
    setEndM: React.Dispatch<React.SetStateAction<number>>;
    alignmentStartM: number;
    alignmentEndM: number;
    onSelect: (options: OnSelectOptions) => void;
    changeTimes: ChangeTimes;
    showArea: (area: BoundingBox) => void;
}> = ({
    alignmentId,
    diagramWidthPx,
    startM,
    endM,
    setStartM,
    setEndM,
    alignmentStartM,
    alignmentEndM,
    onSelect,
    changeTimes,
    showArea,
}) => {
    const delegates = createDelegates(TrackLayoutActions);
    const ref = useRef<HTMLDivElement>(null);
    const alignmentIdChangeTime = useRef(Date.now());
    useEffect(() => {
        alignmentIdChangeTime.current = Date.now();
    }, [alignmentId]);
    /**
     panning is the X pixel value where our last panning movement started, or null if we're not currently panning
     */
    const [panning, setPanning] = useState<null | number>(null);
    const [mousePositionInElement, setMousePositionInElement] = useState<null | [number, number]>(
        null,
    );
    const horizontalTickLengthMeters = minimumIntervalOrLongest(
        diagramWidthPx / (endM - startM),
        minimumApproximateHorizontalTickWidthPx,
    );

    const { heights: kmHeights, alignmentId: heightsLoadedForAlignmentId } = useAlignmentHeights(
        alignmentId,
        changeTimes,
        startM,
        endM,
        horizontalTickLengthMeters,
    ) ?? { alignmentId: undefined, heights: [] };

    const linkingSummary = useLoader(
        () =>
            'planId' in alignmentId
                ? undefined
                : getLocationTrackLinkingSummary(
                      changeTimes.layoutLocationTrack,
                      alignmentId.locationTrackId,
                      alignmentId.publishType,
                  ),
        [alignmentId, changeTimes.layoutLocationTrack],
    );

    const [rawGeometry, geometryLoadedForAlignmentId] = useLoader(
        () =>
            loadGeometry(changeTimes, alignmentId).then(
                (rawGeometry) => rawGeometry && [rawGeometry, alignmentId],
            ),
        [alignmentId, changeTimes.layoutLocationTrack],
    ) ?? [[], undefined];
    const geometry = useMemo(
        () =>
            ('planId' in alignmentId
                ? rawGeometry ?? []
                : processLayoutGeometries(rawGeometry ?? [], linkingSummary ?? [])
            ).sort((a, b) => a.point.station - b.point.station),
        [rawGeometry, linkingSummary],
    );
    const elementPosition = ref.current?.getBoundingClientRect();

    const [bottomHeightTick, topHeightTick] = getBottomAndTopTicks(kmHeights, geometry);

    const coordinates: Coordinates = {
        bottomHeightPaddingPx,
        topHeightTick,
        bottomHeightTick,
        chartHeightPx,
        meterHeightPx:
            (chartHeightPx - topHeightPaddingPx + bottomHeightPaddingPx) /
            (topHeightTick - bottomHeightTick),
        mMeterLengthPxOverM: diagramWidthPx / (endM - startM),
        diagramWidthPx,
        endM,
        fullDiagramHeightPx,
        startM,
        horizontalTickLengthMeters,
    };

    const onMouseMove: React.EventHandler<MouseEvent<unknown>> = (e: MouseEvent<SVGSVGElement>) => {
        const elementBounds = ref.current?.getBoundingClientRect();
        if (elementBounds == null) {
            return;
        }
        setMousePositionInElement([e.clientX - elementBounds.x, e.clientY - elementBounds.y]);
        if (!panning) {
            return;
        }
        const requestedPanDistance = (panning - e.clientX) / coordinates.mMeterLengthPxOverM;
        const panDistance = Math.min(
            alignmentEndM - endM,
            Math.max(alignmentStartM - startM, requestedPanDistance),
        );

        setStartM(startM + panDistance);
        setEndM(endM + panDistance);
        setPanning(e.clientX);
    };

    const onDoubleClick: React.EventHandler<MouseEvent<unknown>> = (e) => {
        const elementLeft = ref.current?.getBoundingClientRect()?.x;
        if (elementLeft == null) {
            return;
        }
        const m = xToM(coordinates, e.clientX - elementLeft);
        const index = findTrackMeterIndexContainingM(m, kmHeights);
        if (index == null) {
            return;
        }
        const [left, right] = getTrackMeterPairAroundIndex(index, kmHeights);
        const proportion = (m - left.m) / (right.m - left.m);
        const point = {
            x: (1 - proportion) * left.point.x + proportion * right.point.x,
            y: (1 - proportion) * left.point.y + proportion * right.point.y,
        };
        showArea(calculateBoundingBoxToShowAroundLocation(point));
    };

    const onWheel: React.EventHandler<WheelEvent<unknown>> = (e) => {
        const elementLeft = ref.current?.getBoundingClientRect()?.x;
        if (elementLeft == null) {
            return;
        }
        const focusM = (e.clientX - elementLeft) / coordinates.mMeterLengthPxOverM + startM;
        // downward should zoom out (push startM/endM further out), upward should zoom in
        // upward = negative delta
        const factor = Math.pow(1.05, e.deltaY * 0.01);
        const leftDistanceM = startM - focusM;
        const rightDistanceM = endM - focusM;
        const newStartM = Math.max(
            alignmentStartM,
            startM - leftDistanceM + factor * leftDistanceM,
        );
        const newEndM = Math.min(alignmentEndM, endM - rightDistanceM + factor * rightDistanceM);

        setStartM(newStartM);
        setEndM(newEndM);
    };

    const drawTangentArrows =
        coordinates.mMeterLengthPxOverM > minimumPixelWidthToDrawTangentArrows;

    const stateIsConsistentByAlignmentId =
        geometryLoadedForAlignmentId === alignmentId && heightsLoadedForAlignmentId === alignmentId;

    const snap =
        stateIsConsistentByAlignmentId &&
        getSnappedPoint(
            mousePositionInElement,
            kmHeights,
            geometry,
            coordinates,
            drawTangentArrows,
        );

    function closeDiagram() {
        delegates.onVerticalGeometryDiagramVisibilityChange(false);
    }

    return (
        <div
            style={{ height: fullDiagramHeightPx, position: 'relative' }}
            ref={ref}
            onMouseDown={(e) => {
                e.preventDefault();
                setPanning(e.clientX);
            }}
            onMouseUp={() => setPanning(null)}
            onMouseMove={onMouseMove}
            onMouseLeave={() => {
                setPanning(null);
                setMousePositionInElement(null);
            }}
            onWheel={onWheel}
            onDoubleClick={onDoubleClick}>
            {snap && elementPosition && (
                <HeightTooltip
                    point={snap}
                    parentElementRect={elementPosition}
                    coordinates={coordinates}
                />
            )}
            <div
                className={styles['vertical-geometry-diagram__close-icon']}
                onClick={() => closeDiagram()}>
                <Icons.Close color={IconColor.INHERIT} size={IconSize.MEDIUM_SMALL} />
            </div>
            <svg
                className={`${styles['vertical-geometry-diagram']} ${
                    panning != null && styles['vertical-geometry-diagram__panning']
                }`}
                height={fullDiagramHeightPx}>
                {stateIsConsistentByAlignmentId ? (
                    <>
                        <HeightLines coordinates={coordinates} />
                        <LabeledTicks trackKmHeights={kmHeights} coordinates={coordinates} />
                        {linkingSummary !== undefined && (
                            <PlanLinking
                                coordinates={coordinates}
                                planLinkingSummary={linkingSummary}
                                onSelect={onSelect}
                            />
                        )}

                        <HeightGraph coordinates={coordinates} kmHeights={kmHeights} />
                        <PviGeometry
                            geometry={geometry}
                            kmHeights={kmHeights}
                            coordinates={coordinates}
                            drawTangentArrows={drawTangentArrows}
                        />
                        <HeightLabels coordinates={coordinates} />
                        <Translate x={0} y={240}>
                            <TrackAddressRuler
                                kmHeights={kmHeights}
                                heightPx={40}
                                coordinates={coordinates}
                            />
                        </Translate>
                        {snap && <PointIndicator point={snap} />}
                    </>
                ) : (
                    <rect
                        x={0}
                        y={0}
                        width={diagramWidthPx}
                        height={fullDiagramHeightPx}
                        fill="grey"
                        opacity="0.8"
                    />
                )}
            </svg>
        </div>
    );
};

export default React.memo(VerticalGeometryDiagramSizeHolder);
