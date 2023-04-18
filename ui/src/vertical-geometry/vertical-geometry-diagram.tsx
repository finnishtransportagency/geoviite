import React, { MouseEvent, useMemo, useRef, useState, WheelEvent } from 'react';
import { PublishType } from 'common/common-model';
import { LoaderStatus, useLoader, useLoaderWithStatus, useTwoPartEffect } from 'utils/react-utils';
import { GeometryAlignmentId, GeometryPlanId, VerticalGeometryItem } from 'geometry/geometry-model';
import {
    AlignmentHeights,
    getGeometryPlanVerticalGeometry,
    getLocationTrackHeights,
    getLocationTrackVerticalGeometry,
    getPlanAlignmentHeights,
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
import { debounceAsync } from 'utils/async-utils';
import { PlanLinking } from 'vertical-geometry/plan-linking';
import { getSnappedPoint } from 'vertical-geometry/snapped-point';
import { Coordinates } from 'vertical-geometry/coordinates';
import { PointIndicator } from 'vertical-geometry/point-indicator';
import { HeightGraph } from 'vertical-geometry/height-graph';
import { HeightTooltip } from 'vertical-geometry/height-tooltip';
import useResizeObserver from 'use-resize-observer';
import { getLocationTrackStartAndEnd } from 'track-layout/layout-location-track-api';
import {
    minimumApproximateHorizontalTickWidthPx,
    minimumIntervalOrLongest,
} from 'vertical-geometry/ticks-at-intervals';

const chartHeightPx = 240;
const topHeightPaddingPx = 120;
const bottomHeightPaddingPx = 0;
const fullDiagramHeightPx = 300;
const minimumPixelWidthToDrawTangentArrows = 0.05;

type VerticalGeometryDiagramAlignmentId =
    | { planId: GeometryPlanId; alignmentId: GeometryAlignmentId }
    | { locationTrackId: LocationTrackId; publishType: PublishType };

interface VerticalGeometryDiagramProps {
    alignmentId: VerticalGeometryDiagramAlignmentId;
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

function processGeometries(
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

function minAndMaxHeights(
    kmHeights: TrackKmHeights[],
    geometry: VerticalGeometryItem[],
): [number, number] {
    if (kmHeights.length !== 0) {
        return kmHeights
            .flatMap(({ trackMeterHeights }) => trackMeterHeights.map(({ height }) => height))
            .reduce(
                ([min, max], height) => [
                    Math.min(min, height ?? min),
                    Math.max(max, height ?? max),
                ],
                [1 / 0, -1 / 0],
            );
    } else {
        // fallback approximation for when we want to display a part of track that does have a PVI aid line going
        // through it, but no plan linkage and hence no heights
        return geometry
            .flatMap((p) => [p.start.height, p.point.height, p.end.height])
            .reduce(
                ([min, max], height) => [Math.min(min, height), Math.max(max, height)],
                [1 / 0, -1 / 0],
            );
    }
}

function loadAlignmentHeights(
    alignmentId: VerticalGeometryDiagramAlignmentId,
    startM: number,
    endM: number,
    horizontalTickLengthMeters: number,
): Promise<AlignmentHeights> {
    return 'planId' in alignmentId
        ? getPlanAlignmentHeights(
              alignmentId.planId,
              alignmentId.alignmentId,
              startM,
              endM,
              horizontalTickLengthMeters,
          )
        : getLocationTrackHeights(
              alignmentId.locationTrackId,
              alignmentId.publishType,
              startM,
              endM,
              horizontalTickLengthMeters,
          );
}

function loadGeometry(
    alignmentId:
        | { planId: GeometryPlanId; alignmentId: GeometryAlignmentId }
        | { locationTrackId: LocationTrackId; publishType: PublishType },
): Promise<VerticalGeometryItem[] | null | undefined> {
    return 'planId' in alignmentId
        ? getGeometryPlanVerticalGeometry(alignmentId.planId).then((allPlanGeometries) =>
              allPlanGeometries?.filter((vgl) => vgl.alignmentId == alignmentId.alignmentId),
          )
        : getLocationTrackVerticalGeometry(alignmentId.locationTrackId, undefined, undefined);
}

const VerticalGeometryDiagramSizeHolder: React.FC<VerticalGeometryDiagramProps> = ({
    alignmentId,
}) => {
    const ref = useRef<HTMLDivElement>(null);
    /**
     startM and endM are the endpoints of the visible parts of the diagram, in m-values (not necessarily hitting the
     m-values of actual points on the alignment)
     */
    const [startM, setStartM] = useState<number>();
    const [endM, setEndM] = useState<number>();
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
            setEndM(startM + (endM - startM) * (width / oldWidth));
        },
    });

    useTwoPartEffect(
        () =>
            'planId' in alignmentId
                ? getPlanAlignmentStartAndEnd(alignmentId.planId, alignmentId.alignmentId)
                : getLocationTrackStartAndEnd(alignmentId.locationTrackId, alignmentId.publishType),
        (startAndEnd) => {
            setStartM(startAndEnd?.start?.point?.m);
            setEndM(startAndEnd?.end?.point?.m);
        },
        [alignmentId],
    );

    return (
        <div ref={ref}>
            {ref.current && startM !== undefined && endM !== undefined && (
                <VerticalGeometryDiagram
                    diagramWidthPx={ref.current.clientWidth}
                    alignmentId={alignmentId}
                    startM={startM}
                    setStartM={setStartM}
                    endM={endM}
                    setEndM={setEndM}
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
}> = ({ alignmentId, diagramWidthPx, startM, endM, setStartM, setEndM }) => {
    const ref = useRef<HTMLDivElement>(null);
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

    const debouncedLoadAlignmentHeights = useMemo(
        () => debounceAsync(loadAlignmentHeights, 250),
        [alignmentId],
    );

    const [alignmentHeights, alignmentHeightsLoadedForAlignmentId] = useLoader(
        () =>
            debouncedLoadAlignmentHeights(
                alignmentId,
                startM,
                endM,
                horizontalTickLengthMeters,
            ).then((r) => {
                return Promise.resolve([r, alignmentId]);
            }),
        [alignmentId, startM, endM, horizontalTickLengthMeters],
    ) ?? [undefined, undefined];

    const [rawGeometry, geometryLoaderStatus] = useLoaderWithStatus(
        () => loadGeometry(alignmentId),
        [alignmentId],
    );
    const geometry = useMemo(
        () =>
            alignmentHeights == undefined || rawGeometry == undefined
                ? undefined
                : // the linking summary is currently only loaded as part of alignmentHeights for convenience; it doesn't
                  // actually change with startM/endM/tickLength
                  processGeometries(rawGeometry, alignmentHeights.linkingSummary),
        [alignmentHeights === undefined, rawGeometry, alignmentId],
    );
    const elementPosition = ref.current?.getBoundingClientRect();

    if (alignmentHeights == undefined || geometry == undefined || elementPosition == undefined) {
        return <svg width={diagramWidthPx} height={fullDiagramHeightPx} />;
    }
    if (
        geometry.length == 0 &&
        !alignmentHeights.kmHeights.some((km) => km.trackMeterHeights.some((h) => h.height != null))
    ) {
        return <>(ei korkeuksia raiteella)</>;
    }

    const { kmHeights, alignmentStartM, alignmentEndM, linkingSummary } = alignmentHeights;
    const mMeterLengthPxOverM = diagramWidthPx / (endM - startM);

    const [minHeight, maxHeight] = minAndMaxHeights(kmHeights, geometry);

    const topHeightTick = Math.ceil(maxHeight);
    const bottomHeightTick = Math.floor(minHeight);
    const meterHeightPx =
        (chartHeightPx - topHeightPaddingPx + bottomHeightPaddingPx) /
        (topHeightTick - bottomHeightTick);

    const coordinates: Coordinates = {
        bottomHeightPaddingPx,
        topHeightTick,
        bottomHeightTick,
        chartHeightPx,
        meterHeightPx,
        mMeterLengthPxOverM,
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
        const requestedPanDistance = (panning - e.clientX) / mMeterLengthPxOverM;
        const panDistance = Math.min(
            alignmentEndM - endM,
            Math.max(alignmentStartM - startM, requestedPanDistance),
        );

        setStartM(startM + panDistance);
        setEndM(endM + panDistance);
        setPanning(e.clientX);
    };

    const onWheel: React.EventHandler<WheelEvent<unknown>> = (e) => {
        const elementLeft = ref.current?.getBoundingClientRect()?.x;
        if (elementLeft == null) {
            return;
        }
        const focusM = (e.clientX - elementLeft) / mMeterLengthPxOverM + startM;
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
    const snap = getSnappedPoint(
        mousePositionInElement,
        kmHeights,
        geometry,
        coordinates,
        drawTangentArrows,
    );

    return (
        <div
            style={{ height: fullDiagramHeightPx }}
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
            onWheel={onWheel}>
            {snap && elementPosition && (
                <HeightTooltip
                    point={snap}
                    elementPosition={elementPosition}
                    coordinates={coordinates}
                />
            )}
            <svg
                className={`${styles['vertical-geometry-diagram']} ${
                    panning != null && styles['vertical-geometry-diagram__panning']
                }`}
                height={fullDiagramHeightPx}>
                <HeightLines coordinates={coordinates} />
                <LabeledTicks trackKmHeights={kmHeights} coordinates={coordinates} />
                {'locationTrackId' in alignmentId && (
                    <PlanLinking coordinates={coordinates} planLinkingSummary={linkingSummary} />
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
                {(geometryLoaderStatus === LoaderStatus.Ready &&
                    alignmentHeightsLoadedForAlignmentId === alignmentId) || (
                    <rect
                        x={0}
                        y={0}
                        width={diagramWidthPx}
                        height={fullDiagramHeightPx}
                        fill="grey"
                        opacity={0.8}
                    />
                )}
            </svg>
        </div>
    );
};

export default React.memo(VerticalGeometryDiagramSizeHolder);
