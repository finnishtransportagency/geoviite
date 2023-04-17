import React, { MouseEvent, useRef, useState, WheelEvent } from 'react';
import { KmNumber, PublishType, TrackMeter } from 'common/common-model';
import { formatTrackMeter, formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import { useLoader } from 'utils/react-utils';
import { GeometryAlignmentId, GeometryPlanId, VerticalGeometryItem } from 'geometry/geometry-model';
import {
    getGeometryPlanVerticalGeometry,
    getLocationTrackHeights,
    getLocationTrackVerticalGeometry,
    getPlanAlignmentHeights,
} from 'geometry/geometry-api';
import styles from './vertical-geometry-diagram.scss';
import { minimumIndexBy } from 'utils/array-utils';
import {
    findTrackMeterIndexContainingM,
    getTrackMeterPairAroundIndex,
    TrackMeterIndex,
} from 'vertical-geometry/track-meter-index';
import { Translate } from 'vertical-geometry/translate';
import { TrackAddressRuler } from 'vertical-geometry/track-address-ruler';
import { HeightLines } from 'vertical-geometry/height-lines';
import { PviGeometry } from 'vertical-geometry/pvi-geometry';
import { LabeledTicks } from 'vertical-geometry/labeled-ticks';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { debounceAsync } from 'utils/async-utils';
import { PlanLinking } from 'vertical-geometry/plan-linking';

const maxHorizontalTickDensity = 100;
const diagramWidthPx = 1200;
const chartHeightPx = 240;
const topHeightPaddingPx = 120;
const bottomHeightPaddingPx = 0;
const fullDiagramHeightPx = 300;

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

export function enumerateInStepsUpTo(start: number, stepSize: number, upTo: number) {
    const rv: number[] = [];
    for (let x = start; x <= upTo; x += stepSize) {
        rv.push(x);
    }
    return rv;
}

export interface AlignmentHeights {
    kmHeights: TrackKmHeights[];
    alignmentStartM: number;
    alignmentEndM: number;
    linkingSummary: PlanLinkingSummaryItem[];
}

function chooseHorizontalTickLengthMeters(distanceMeters: number): number {
    const firstTooDenseIndex = horizontalTickLengthsMeters.findIndex(
        (widthM) => distanceMeters / widthM > maxHorizontalTickDensity,
    );
    return firstTooDenseIndex == -1
        ? horizontalTickLengthsMeters[horizontalTickLengthsMeters.length - 1]
        : horizontalTickLengthsMeters[Math.max(0, firstTooDenseIndex - 1)];
}

function closestGeometryM(m: number, geometry: VerticalGeometryItem[]) {
    const allGeometryMs = geometry.flatMap((geom) =>
        geom.point.address == null || geom.start.address == null || geom.end.address == null
            ? []
            : ([
                  [geom.point.station, geom.point.address],
                  [geom.start.station, geom.start.address],
                  [geom.end.station, geom.end.address],
              ] as const),
    );
    const minIndex = minimumIndexBy(allGeometryMs, (geometryM) => Math.abs(m - geometryM[0]));
    return minIndex == null ? null : allGeometryMs[minIndex];
}

function closestRulerTickM(
    m: number,
    trackKmHeights: TrackKmHeights[],
): [number, TrackMeter] | null {
    const index = findTrackMeterIndexContainingM(m, trackKmHeights);
    if (index == null) {
        return null;
    }
    const [left, right] = getTrackMeterPairAroundIndex(index, trackKmHeights);
    const leftTrackMeter = {
        kmNumber: trackKmHeights[index.left.kmIndex].kmNumber,
        meters: left.meter,
    };
    if (right == null || index.right == null) {
        return [left.m, leftTrackMeter];
    } else {
        const rightTrackMeter = {
            kmNumber: trackKmHeights[index.right.kmIndex].kmNumber,
            meters: right.meter,
        };
        return Math.abs(m - left.m) < Math.abs(m - right.m)
            ? [left.m, leftTrackMeter]
            : [right.m, rightTrackMeter];
    }
}

interface SnappedPoint {
    mouseCursorOverArea: 'ruler' | 'chart';
    m: number;
    xPositionPx: number;
    yPositionPx: number;
    height: number;
    address: TrackMeter;
    didSnap: boolean;
}

function getSnappedPoint(
    mousePositionInElement: [number, number] | null,
    trackKmHeights: TrackKmHeights[],
    geometry: VerticalGeometryItem[],
    coordinates: Coordinates,
): SnappedPoint | null {
    if (mousePositionInElement == null) {
        return null;
    }
    const [mouseX, mouseY] = mousePositionInElement;
    const mouseCursorOverArea = mouseY > coordinates.chartHeightPx ? 'ruler' : 'chart';
    const xCoordinateM = xToM(coordinates, mouseX);
    const maxSnapDistanceM = coordinates.horizontalTickLengthMeters / 2;

    const snappedAddress =
        mouseCursorOverArea === 'ruler'
            ? closestRulerTickM(xCoordinateM, trackKmHeights)
            : closestGeometryM(xCoordinateM, geometry);
    if (snappedAddress == null) {
        return null;
    }

    const [[m, address], didSnap] =
        Math.abs(snappedAddress[0] - xCoordinateM) > maxSnapDistanceM
            ? ([
                  [
                      xCoordinateM,
                      approximateTrackAddressAt(
                          xCoordinateM,
                          // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                          findTrackMeterIndexContainingM(xCoordinateM, trackKmHeights)!,
                          trackKmHeights,
                      ),
                  ] as const,
                  false,
              ] as const)
            : ([snappedAddress, true] as const);

    const kmIndex = findTrackMeterIndexContainingM(m, trackKmHeights);
    if (kmIndex == null) {
        return null;
    }

    const height = approximateHeightAt(m, kmIndex, trackKmHeights);

    if (height == null || address == null) {
        return null;
    }
    const x = mToX(coordinates, m);
    const y = heightToY(coordinates, height);

    return { mouseCursorOverArea, m, xPositionPx: x, yPositionPx: y, didSnap, height, address };
}

const PointIndicator: React.FC<{ point: SnappedPoint }> = ({ point }) => {
    const crossLength = 4;
    return (
        <>
            <line
                fill="none"
                stroke="blue"
                strokeWidth={2}
                x1={point.xPositionPx - crossLength}
                x2={point.xPositionPx + crossLength}
                y1={point.yPositionPx - crossLength}
                y2={point.yPositionPx + crossLength}
            />
            <line
                fill="none"
                stroke="blue"
                strokeWidth={2}
                x1={point.xPositionPx - crossLength}
                x2={point.xPositionPx + crossLength}
                y1={point.yPositionPx + crossLength}
                y2={point.yPositionPx - crossLength}
            />
        </>
    );
};

const HeightTooltip: React.FC<{
    point: SnappedPoint;
    elementPosition: DOMRect;
    coordinates: Coordinates;
}> = ({ point, elementPosition, coordinates }) => {
    const displayedAddress =
        point.mouseCursorOverArea == 'chart' &&
        (point.didSnap || coordinates.mMeterLengthPxOverM > 20)
            ? formatTrackMeter(point.address)
            : formatTrackMeterWithoutMeters(point.address);
    return (
        <div
            style={{
                position: 'absolute',
                left: elementPosition.left + point.xPositionPx + 20,
                top: elementPosition.top + point.yPositionPx + 20,
                background: 'grey',
                padding: '2px',
            }}>
            {displayedAddress}
            <br />
            kt=
            {point.height.toLocaleString(undefined, {
                maximumFractionDigits: 2,
            })}
        </div>
    );
};

function makeHeightGraph(coordinates: Coordinates, kmHeights: TrackKmHeights[]): JSX.Element {
    const lines: JSX.Element[] = [];
    let linePoints: [number, number][] = [];
    let lineIndex = 0;
    const finishLineIfStarted = () => {
        if (linePoints.length > 0) {
            lines.push(
                <polyline
                    key={lineIndex++}
                    points={polylinePoints(linePoints)}
                    stroke="black"
                    fill="none"
                    strokeWidth={2}
                />,
            );
            linePoints = [];
        }
    };
    for (const { trackMeterHeights } of kmHeights) {
        for (const { height, m } of trackMeterHeights) {
            if (height == null) {
                finishLineIfStarted();
            } else {
                linePoints.push([mToX(coordinates, m), heightToY(coordinates, height)]);
            }
        }
    }
    finishLineIfStarted();
    return <>{lines}</>;
}

const loadAlignmentHeights: (
    alignmentId: VerticalGeometryDiagramAlignmentId,
    startM: number,
    endM: number,
    horizontalTickLengthMeters: number,
) => Promise<AlignmentHeights> = debounceAsync(
    (
        alignmentId: VerticalGeometryDiagramAlignmentId,
        startM: number,
        endM: number,
        horizontalTickLengthMeters: number,
    ) =>
        'planId' in alignmentId
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
              ),
    250,
);

export const VerticalGeometryDiagram: React.FC<VerticalGeometryDiagramProps> = ({
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

    const alignmentHeights = useLoader(
        () => loadAlignmentHeights(alignmentId, startM, endM, horizontalTickLengthMeters),
        [alignmentId, startM, endM, horizontalTickLengthMeters],
    );

    const geometry = useLoader(
        () =>
            ('planId' in alignmentId
                ? getGeometryPlanVerticalGeometry(alignmentId.planId).then((allPlanGeometries) =>
                      allPlanGeometries?.filter(
                          (vgl) => vgl.alignmentId == alignmentId.alignmentId,
                      ),
                  )
                : getLocationTrackVerticalGeometry(
                      alignmentId.locationTrackId,
                      undefined,
                      undefined,
                  )
            ).then(
                (geometry) => (geometry == null ? null : geometry), //.sort((a, b) => b.point.station - a.point.station),
            ),
        [alignmentId],
    );

    if (alignmentHeights == undefined || geometry == undefined) {
        return (
            <svg
                width={diagramWidthPx}
                height={fullDiagramHeightPx}
                style={{ border: '1px solid red' }}
            />
        );
    }
    const { kmHeights, alignmentStartM, alignmentEndM, linkingSummary } = alignmentHeights;
    const mMeterLengthPxOverM = diagramWidthPx / (endM - startM);

    const [minHeight, maxHeight] = kmHeights
        .flatMap(({ trackMeterHeights }) => trackMeterHeights.map(({ height }) => height))
        .reduce(
            ([min, max], height) => [Math.min(min, height ?? min), Math.max(max, height ?? max)],
            [1 / 0, -1 / 0],
        );

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

    const heightGraph = makeHeightGraph(coordinates, kmHeights);

    const snap = getSnappedPoint(mousePositionInElement, kmHeights, geometry, coordinates);

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
                height={fullDiagramHeightPx}
                style={{ border: '1px solid red' }}>
                <HeightLines coordinates={coordinates} />
                <LabeledTicks trackKmHeights={kmHeights} coordinates={coordinates} />
                {'locationTrackId' in alignmentId && (
                    <PlanLinking coordinates={coordinates} planLinkingSummary={linkingSummary} />
                )}
                {heightGraph}
                <PviGeometry geometry={geometry} kmHeights={kmHeights} coordinates={coordinates} />
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

export interface TrackMeterHeight {
    /** m-value in entire alignment */
    m: number;
    meter: number;
    height: number | null;
}

export interface PlanLinkingSummaryItem {
    startM: number;
    endM: number;
    filename: string | null;
}

export interface TrackKmHeights {
    kmNumber: KmNumber;
    trackMeterHeights: TrackMeterHeight[];
}

export function approximateHeightAtM(m: number, kmHeights: TrackKmHeights[]): number | null {
    const index = findTrackMeterIndexContainingM(m, kmHeights);
    if (index == null) {
        return null;
    }
    return approximateHeightAt(m, index, kmHeights);
}

function approximateHeightAt(
    m: number,
    index: TrackMeterIndex,
    kmHeights: TrackKmHeights[],
): number | null {
    const [leftMeter, rightMeter] = getTrackMeterPairAroundIndex(index, kmHeights);
    if (rightMeter == null || rightMeter.height == null) {
        // could extrapolate from previous angle here, but probably better solution would be to make the track
        // meter range we return from the backend closed
        return leftMeter.height;
    }
    if (leftMeter.height == null) {
        return rightMeter.height;
    }
    const proportion = (m - leftMeter.m) / (rightMeter.m - leftMeter.m);
    return (1 - proportion) * leftMeter.height + proportion * rightMeter.height;
}

function approximateTrackAddressAt(
    m: number,
    index: TrackMeterIndex,
    kmHeights: TrackKmHeights[],
): TrackMeter | null {
    const [leftMeter, rightMeter] = getTrackMeterPairAroundIndex(index, kmHeights);
    const leftKm = kmHeights[index.left.kmIndex];
    if (rightMeter == null || index.left.kmIndex !== index.right?.kmIndex) {
        // same as above in approximateHeightAt: This extrapolation shouldn't exist, should instead just get the
        // endpoints from the backend. Or in the case where we're passing over a track kilometer change, we can't tell
        // apart the effects of stretched/squeezed track meters from the effect of the km post being placed not exactly
        // a km from the last; so for now, we just do naive extrapolation, but the answer would be to not extrapolate
        // at all and just get the actual track address from geocoding instead
        return { kmNumber: leftKm.kmNumber, meters: leftMeter.meter + m - leftMeter.m };
    }
    const proportion = (m - leftMeter.m) / (rightMeter.m - leftMeter.m);
    return {
        kmNumber: leftKm.kmNumber,
        meters: leftMeter.meter + proportion * (rightMeter.meter - leftMeter.meter),
    };
}

export interface Coordinates {
    startM: number;
    endM: number;
    meterHeightPx: number;
    mMeterLengthPxOverM: number;
    fullDiagramHeightPx: number;
    diagramWidthPx: number;
    horizontalTickLengthMeters: number;

    bottomHeightPaddingPx: number;
    bottomHeightTick: number;
    topHeightTick: number;
    chartHeightPx: number;
}

export function mToX(coordinates: Coordinates, m: number): number {
    return (m - coordinates.startM) * coordinates.mMeterLengthPxOverM;
}

function xToM(coordinates: Coordinates, x: number): number {
    return x / coordinates.mMeterLengthPxOverM + coordinates.startM;
}

export function heightToY(coordinates: Coordinates, height: number): number {
    return (
        coordinates.chartHeightPx -
        coordinates.bottomHeightPaddingPx +
        (coordinates.bottomHeightTick - height) * coordinates.meterHeightPx
    );
}

// untested! Implementation written by simply inverting heightToY's arithmetic operations and figuring that maybe that
// gets the inverse function
// eslint-disable-next-line @typescript-eslint/no-unused-vars
function yToHeight(coordinates: Coordinates, y: number): number {
    return (
        coordinates.bottomHeightTick -
        (y - coordinates.chartHeightPx + coordinates.bottomHeightPaddingPx) /
            coordinates.meterHeightPx
    );
}

export function polylinePoints(points: readonly (readonly [number, number])[]): string {
    return points.map(([x, y]) => `${x},${y}`).join(' ');
}
