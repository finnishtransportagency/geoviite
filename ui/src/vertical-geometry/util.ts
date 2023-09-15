import { PlanLinkingSummaryItem, TrackKmHeights } from 'geometry/geometry-api';
import {
    findTrackMeterIndexContainingM,
    getTrackMeterPairAroundIndex,
    TrackMeterIndex,
} from 'vertical-geometry/track-meter-index';
import { VerticalGeometryItem } from 'geometry/geometry-model';
import { filterNotEmpty, findLastIndex } from 'utils/array-utils';
import { linearFunctionFromPoints } from 'utils/math-utils';

type HeightValue = number;
type LowerHeightBound = number;
type UpperHeightBound = number;
type HeightBounds = [LowerHeightBound, UpperHeightBound];

export function approximateHeightAtM(m: number, kmHeights: TrackKmHeights[]): number | undefined {
    const index = findTrackMeterIndexContainingM(m, kmHeights);
    if (index == undefined) {
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
    if (rightMeter.height == undefined) {
        return leftMeter.height;
    }
    if (leftMeter.height == undefined) {
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
    geometry: VerticalGeometryItem[],
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
        const approximateHeightAtLeftEdge = approximateHeightAtVerticalGeometryDiagramLeftEdge(
            heightBoundsFromTrackKm,
            geometry,
            visibleStartM,
        );

        const approximateHeightAtRightEdge = approximateHeightAtVerticalGeometryDiagramRightEdge(
            heightBoundsFromTrackKm,
            geometry,
            visibleEndM,
        );

        heightBoundsFromTrackKm = heightsToBounds(
            [
                ...heightBoundsFromTrackKm,
                approximateHeightAtLeftEdge,
                approximateHeightAtRightEdge,
            ].filter((height): height is number => height !== undefined),
        );
    }

    return (
        heightBoundsFromTrackKm ??
        heightsToBounds(
            geometry.flatMap((p) => [p.start.height, p.point.height, p.end.height]),
        ) ?? [0, 100]
    );
}

function heightIsOutOfBounds(heightBounds: HeightBounds, height: HeightValue) {
    return height && (height < heightBounds[0] || height > heightBounds[1]);
}

function approximateHeightAtVerticalGeometryDiagramLeftEdge(
    heightBounds: HeightBounds,
    geometry: VerticalGeometryItem[],
    visibleStartM: number,
): number | undefined {
    const indexOfFirstVisibleGeometryItem = geometry.findIndex((geometryItem) => {
        return geometryItem.start.station > visibleStartM;
    });

    if (indexOfFirstVisibleGeometryItem > 0) {
        const lastNotVisibleGeometryItem = geometry[indexOfFirstVisibleGeometryItem - 1];
        const firstVisibleGeometryItem = geometry[indexOfFirstVisibleGeometryItem];

        if (heightIsOutOfBounds(heightBounds, lastNotVisibleGeometryItem.end.height)) {
            const linearFunctionFromLastKnownHeightToNextKnownHeight = linearFunctionFromPoints(
                {
                    x: lastNotVisibleGeometryItem.end.station,
                    y: lastNotVisibleGeometryItem.end.height,
                },
                {
                    x: firstVisibleGeometryItem.start.station,
                    y: firstVisibleGeometryItem.start.height,
                },
                lastNotVisibleGeometryItem.end.height,
            );

            // Approximate height at the left edge of the vertical geometry diagram.
            return linearFunctionFromLastKnownHeightToNextKnownHeight(
                visibleStartM - lastNotVisibleGeometryItem.end.station,
            );
        }
    }

    return undefined;
}

function approximateHeightAtVerticalGeometryDiagramRightEdge(
    heightBounds: HeightBounds,
    geometry: VerticalGeometryItem[],
    visibleEndM: number,
): number | undefined {
    const indexOfLastVisibleGeometryItem = findLastIndex(geometry, (geometryItem) => {
        return geometryItem.end.station < visibleEndM;
    });

    if (
        indexOfLastVisibleGeometryItem >= 0 &&
        indexOfLastVisibleGeometryItem + 1 < geometry.length
    ) {
        const lastVisibleGeometryItem = geometry[indexOfLastVisibleGeometryItem];
        const firstNotVisibleGeometryItem = geometry[indexOfLastVisibleGeometryItem + 1];

        if (heightIsOutOfBounds(heightBounds, firstNotVisibleGeometryItem.start.height)) {
            const linearFunctionFromLastKnownHeightToNextKnownHeight = linearFunctionFromPoints(
                {
                    x: lastVisibleGeometryItem.end.station,
                    y: lastVisibleGeometryItem.end.height,
                },
                {
                    x: firstNotVisibleGeometryItem.start.station,
                    y: firstNotVisibleGeometryItem.start.height,
                },
                lastVisibleGeometryItem.end.height,
            );

            // Approximate height at the right edge of the vertical geometry diagram.
            return linearFunctionFromLastKnownHeightToNextKnownHeight(
                visibleEndM - lastVisibleGeometryItem.end.station,
            );
        }
    }

    return undefined;
}

export function zeroSafeDivision(a: number, b: number): number {
    return b === 0 ? 0 : a / b;
}

export function sumPaddings(p1: string, p2: string) {
    return parseFloat(p1) + parseFloat(p2);
}

export function substituteLayoutStationsForGeometryStations(
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
                geom.fileName === linkedAreaSourceFile(geom.start.station) ||
                geom.fileName === linkedAreaSourceFile(geom.end.station) ||
                geom.fileName === linkedAreaSourceFile(geom.point.station),
        );
}
