import React, { MouseEvent, useMemo, useRef, useState, WheelEvent } from 'react';
import { PublishType } from 'common/common-model';
import { useLoader } from 'utils/react-utils';
import { GeometryAlignmentId, GeometryPlanId, VerticalGeometryItem } from 'geometry/geometry-model';
import {
    AlignmentHeights,
    getGeometryPlanVerticalGeometry,
    getLocationTrackHeights,
    getLocationTrackVerticalGeometry,
    getPlanAlignmentHeights,
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

const maxHorizontalTickDensity = 100;
const diagramWidthPx = 1200;
const chartHeightPx = 240;
const topHeightPaddingPx = 120;
const bottomHeightPaddingPx = 0;
const fullDiagramHeightPx = 300;
const minimumPixelWidthToDrawTangentArrows = 0.05;

// units of track meter distance; but the tick length is chosen based on m-value distances, so curved side tracks can
// stretch or shrink on the diagram
// values beyond 1000 don't make much sense, as the backend returns at least a starting tick for each track kilometer
// in order to keep in sync with track addressing
// values have to be integers for backend implementation simplicity
const horizontalTickLengthsMeters = [1000, 500, 250, 100, 50, 25, 10, 5, 2, 1];

type VerticalGeometryDiagramAlignmentId =
    | { planId: GeometryPlanId; alignmentId: GeometryAlignmentId }
    | { locationTrackId: LocationTrackId; publishType: PublishType };

interface VerticalGeometryDiagramProps {
    alignmentId: VerticalGeometryDiagramAlignmentId;
    initialStartM: number;
    initialEndM: number;
}

function chooseHorizontalTickLengthMeters(distanceMeters: number): number {
    const firstTooDenseIndex = horizontalTickLengthsMeters.findIndex(
        (widthM) => distanceMeters / widthM > maxHorizontalTickDensity,
    );
    return firstTooDenseIndex == -1
        ? horizontalTickLengthsMeters[horizontalTickLengthsMeters.length - 1]
        : horizontalTickLengthsMeters[Math.max(0, firstTooDenseIndex - 1)];
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
        : getLocationTrackVerticalGeometry(alignmentId.locationTrackId, undefined, undefined).then(
              (geometry) =>
                  geometry == null
                      ? null
                      : geometry.map(substituteLayoutStationsForGeometryStations),
          );
}

const VerticalGeometryDiagramBase: React.FC<VerticalGeometryDiagramProps> = ({
    initialStartM,
    initialEndM,
    alignmentId,
}) => {
    const ref = useRef<HTMLDivElement>(null);
    /**
     startM and endM are the endpoints of the visible parts of the diagram, in m-values (not necessarily hitting the
     m-values of actual points on the alignment)
     */
    const [startM, setStartM] = useState(initialStartM);
    const [endM, setEndM] = useState(initialEndM);
    /**
     panning is the X pixel value where our last panning movement started, or null if we're not currently panning
     */
    const [panning, setPanning] = useState<null | number>(null);
    const [mousePositionInElement, setMousePositionInElement] = useState<null | [number, number]>(
        null,
    );
    const horizontalTickLengthMeters = chooseHorizontalTickLengthMeters(endM - startM);

    const debouncedLoadAlignmentHeights = useMemo(
        () => debounceAsync(loadAlignmentHeights, 250),
        [alignmentId],
    );

    const alignmentHeights = useLoader(
        () => debouncedLoadAlignmentHeights(alignmentId, startM, endM, horizontalTickLengthMeters),
        [alignmentId, startM, endM, horizontalTickLengthMeters],
    );

    const geometry = useLoader(() => loadGeometry(alignmentId), [alignmentId]);

    if (alignmentHeights == undefined || geometry == undefined) {
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

    const elementPosition = ref.current?.getBoundingClientRect();

    return (
        <div
            style={{ width: diagramWidthPx, height: fullDiagramHeightPx }}
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
                viewBox="0 0 1200 300"
                width={diagramWidthPx}
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
            </svg>
        </div>
    );
};

export const VerticalGeometryDiagram = React.memo(VerticalGeometryDiagramBase);
