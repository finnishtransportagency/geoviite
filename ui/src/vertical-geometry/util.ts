import { PlanLinkingSummaryItem, TrackKmHeights } from 'geometry/geometry-api';
import {
    findTrackMeterIndexContainingM,
    getTrackMeterPairAroundIndex,
    TrackMeterIndex,
} from 'vertical-geometry/track-meter-index';
import { VerticalGeometryDiagramDisplayItem, VerticalGeometryItem } from 'geometry/geometry-model';
import { filterNotEmpty, findLastIndex } from 'utils/array-utils';
import { linearFunction, slopeFromPoints } from 'utils/math-utils';
import { Point } from 'model/geometry';

type HeightValue = number;
type LowerHeightBound = number;
type UpperHeightBound = number;
type HeightBounds = [LowerHeightBound, UpperHeightBound];

type AdjacentVerticalGeometryItems = [
    VerticalGeometryDiagramDisplayItem | undefined,
    VerticalGeometryDiagramDisplayItem | undefined,
];

export function approximateHeightAtM(m: number, kmHeights: TrackKmHeights[]): number | undefined {
    const index = findTrackMeterIndexContainingM(m, kmHeights);
    if (index === undefined) {
        return undefined;
    }
    return approximateHeightAt(m, index, kmHeights);
}

export function approximateHeightAt(
    m: number,
    index: TrackMeterIndex,
    kmHeights: TrackKmHeights[],
): number | undefined {
    const [leftMeter, rightMeter] = getTrackMeterPairAroundIndex(index, kmHeights);
    // We don't try to extrapolate heights; this is why the back-end puts in some extra effort to make sure to send
    // heights to cover all intervals where we might want to display heights (and hence can always interpolate)
    if (rightMeter.height === undefined) {
        return leftMeter.height;
    }
    if (leftMeter.height === undefined) {
        return rightMeter.height;
    }
    const proportion = (m - leftMeter.m) / (rightMeter.m - leftMeter.m);
    return (1 - proportion) * leftMeter.height + proportion * rightMeter.height;
}

export function polylinePoints(points: readonly (readonly [number, number])[]): string {
    return points.map(([x, y]) => `${x},${y}`).join(' ');
}

export function getBottomAndTopTicks(
    kmHeights: TrackKmHeights[],
    geometry: VerticalGeometryDiagramDisplayItem[],
    visibleStartM: number,
    visibleEndM: number,
): [number, number] {
    const heightsToBounds = (heights: number[]): HeightBounds | undefined =>
        heights.length === 0
            ? undefined
            : [Math.floor(Math.min(...heights)), Math.ceil(Math.max(...heights))];
    // most of the time we have some visible heights on the track km; but sometimes there are holes in the linking
    // reaching across a whole track km and we're zoomed into it, in which case we'll fall back to using height bounds
    // calculated from the geometry; and sometimes there are no heights at all, in which case we don't need to worry
    // about how tall to display them anyway and can just use a (suitably strange) fallback
    let heightBoundsFromTrackKm = heightsToBounds(
        kmHeights
            .flatMap(({ trackMeterHeights }) => trackMeterHeights.map(({ height }) => height))
            .filter(filterNotEmpty),
    );

    // The vertical geometry diagram height bounds calculation should also account for the lines between the previously
    // displayed known height value and the next known height value out of view (from the left or right edges of the
    // vertical geometry diagram).
    //
    // When this was unaccounted for, especially long transfer lines with "unknown" height values in between known
    // height values resulted in the transfer line being drawn to the top or the bottom edge of the vertical geometry
    // diagram. This was unwanted behavior as the vertical geometry diagram's height should scale correctly even with
    // large changes between values, which is what the following condition should account for.
    if (heightBoundsFromTrackKm) {
        heightBoundsFromTrackKm = heightsToBounds(
            [
                ...heightBoundsFromTrackKm,
                approximateHeightAtLeftEdge(heightBoundsFromTrackKm, geometry, visibleStartM),
                approximateHeightAtRightEdge(heightBoundsFromTrackKm, geometry, visibleEndM),
            ].filter(filterNotEmpty),
        );
    }

    return (
        heightBoundsFromTrackKm ??
        heightsToBounds(
            geometry
                .flatMap((p) => [p.start?.height, p.point?.height, p.end?.height])
                .filter(filterNotEmpty),
        ) ?? [0, 100]
    );
}

function heightIsOutOfBounds(heightBounds: HeightBounds, height: HeightValue) {
    return height && (height < heightBounds[0] || height > heightBounds[1]);
}

function approximateHeightAtLeftEdge(
    previousHeightBounds: HeightBounds,
    geometry: VerticalGeometryDiagramDisplayItem[],
    visibleStartM: number,
): number | undefined {
    const firstVisibleItemIndex = geometry.findIndex((item) =>
        item.start ? item.start.station > visibleStartM : false,
    );

    return approximateHeight(
        previousHeightBounds,
        [geometry[firstVisibleItemIndex - 1], geometry[firstVisibleItemIndex]],
        visibleStartM,
    );
}

function approximateHeightAtRightEdge(
    previousHeightBounds: HeightBounds,
    geometry: VerticalGeometryDiagramDisplayItem[],
    visibleEndM: number,
): number | undefined {
    const lastVisibleItemIndex = findLastIndex(geometry, (item) =>
        item.end ? item.end.station < visibleEndM : false,
    );

    return approximateHeight(
        previousHeightBounds,
        [geometry[lastVisibleItemIndex], geometry[lastVisibleItemIndex + 1]],
        visibleEndM,
    );
}

function approximateHeight(
    previousHeightBounds: HeightBounds,
    adjacentVerticalGeometryItems: AdjacentVerticalGeometryItems,
    evaluateHeightAtX: number,
): number | undefined {
    if (!adjacentVerticalGeometryItems[0]?.end || !adjacentVerticalGeometryItems[1]?.start) {
        return undefined;
    }

    const [previousPoint, nextPoint]: [Point, Point] = [
        {
            x: adjacentVerticalGeometryItems[0].end.station,
            y: adjacentVerticalGeometryItems[0].end.height,
        },
        {
            x: adjacentVerticalGeometryItems[1].start.station,
            y: adjacentVerticalGeometryItems[1].start.height,
        },
    ];

    if (
        !heightIsOutOfBounds(previousHeightBounds, previousPoint.y) &&
        !heightIsOutOfBounds(previousHeightBounds, nextPoint.y)
    ) {
        // Optimization: Neither of the points is out of bounds in the y-axis,
        // meaning that a straight line between them is not going to be either.
        return undefined;
    }

    return linearFunction(
        slopeFromPoints(previousPoint, nextPoint),
        previousPoint.y,
    )(evaluateHeightAtX - previousPoint.x);
}

export function zeroSafeDivision(a: number, b: number): number {
    return b === 0 ? 0 : a / b;
}

export function sumPaddings(p1: string, p2: string) {
    return parseFloat(p1) + parseFloat(p2);
}

export function substituteLayoutStationsForGeometryStations(
    geometryItem: VerticalGeometryItem,
): VerticalGeometryDiagramDisplayItem {
    return {
        ...geometryItem,

        start: geometryItem.layoutStartStation
            ? {
                  ...geometryItem.start,
                  station: geometryItem.layoutStartStation,
              }
            : undefined,

        point: geometryItem.layoutPointStation
            ? {
                  ...geometryItem.point,
                  station: geometryItem.layoutPointStation,
              }
            : undefined,

        end: geometryItem.layoutEndStation
            ? {
                  ...geometryItem.end,
                  station: geometryItem.layoutEndStation,
              }
            : undefined,
    };
}

export function processLayoutGeometries(
    geometry: VerticalGeometryItem[],
    linkingSummary: PlanLinkingSummaryItem[],
) {
    const linkedAreaSourceFile = (layoutM: number) =>
        linkingSummary.find((linkingSummaryItem) => linkingSummaryItem.endM >= layoutM)?.filename;

    return geometry
        .map(substituteLayoutStationsForGeometryStations)
        .filter(
            (geom) =>
                (geom.start?.station &&
                    geom.fileName === linkedAreaSourceFile(geom.start.station)) ||
                (geom.end?.station && geom.fileName === linkedAreaSourceFile(geom.end.station)) ||
                (geom.point?.station && geom.fileName === linkedAreaSourceFile(geom.point.station)),
        );
}
