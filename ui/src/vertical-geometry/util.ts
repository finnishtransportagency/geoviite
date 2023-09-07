import { PlanLinkingSummaryItem, TrackKmHeights } from 'geometry/geometry-api';
import {
    findTrackMeterIndexContainingM,
    getTrackMeterPairAroundIndex,
    TrackMeterIndex,
} from 'vertical-geometry/track-meter-index';
import { VerticalGeometryItem } from 'geometry/geometry-model';
import { filterNotEmpty } from 'utils/array-utils';

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
