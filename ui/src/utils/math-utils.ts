import { Point } from 'model/geometry';
import { LayoutPoint } from 'track-layout/track-layout-model';

export function directionBetweenPoints(p1: Point, p2: Point): number {
    return Math.atan2(p2.y - p1.y, p2.x - p1.x);
}

export function angleDiffRads(rads1: number, rads2: number): number {
    const diff = normalizeRads(rads2 - rads1);
    return diff > Math.PI ? 2 * Math.PI - diff : diff;
}

function normalizeRads(rads: number): number {
    return normalizeToRangeNonInclusive(rads, 2 * Math.PI);
}

function normalizeToRangeNonInclusive(value: number, range: number): number {
    return value < 0 || value >= range ? value - range * Math.floor(value / range) : value;
}

export function radsToDegrees(rads: number): number {
    return (180 / Math.PI) * rads;
}

export function getPartialPolyLine(
    points: LayoutPoint[],
    startM: number,
    endM: number,
): number[][] {
    const start = findOrInterpolateXY(points, startM);
    const end = findOrInterpolateXY(points, endM);
    // If both ends are interpolated between the same 2 points or are the same point, return nothing
    if (start == undefined || end == undefined || start.low >= end.high) return [];
    const midStart = start.low == start.high ? start.high + 1 : start.high;
    const midEnd = end.low == end.high ? end.low : end.low + 1;
    const midPoints =
        midStart >= 0 && midStart < midEnd
            ? points.slice(midStart, midEnd).map((p) => [p.x, p.y])
            : [];
    return [start.point, ...midPoints, end.point];
}

type SeekResult = {
    high: number;
    low: number;
    point: number[];
};

export function findOrInterpolateXY(points: LayoutPoint[], mValue: number): SeekResult | undefined {
    const lastIndex = points.length - 1;
    if (points.length < 2) return undefined;
    if (points[0].m >= mValue)
        return {
            low: 0,
            high: 0,
            point: [points[0].x, points[0].y],
        };
    if (points[lastIndex].m <= mValue)
        return {
            low: lastIndex,
            high: lastIndex,
            point: [points[lastIndex].x, points[lastIndex].y],
        };
    let low = 0;
    let high = lastIndex;
    while (low < high - 1) {
        const mid = Math.floor((low + high) / 2);
        if (points[mid].m > mValue) high = mid;
        else if (points[mid].m < mValue) low = mid;
        else
            return {
                low: mid,
                high: mid,
                point: [points[mid].x, points[mid].y],
            };
    }
    return {
        low: low,
        high: high,
        point: interpolateXY(points[low], points[high], mValue),
    };
}

export function interpolateXY(point1: LayoutPoint, point2: LayoutPoint, mValue: number): number[] {
    if (mValue < point1.m || mValue > point2.m)
        throw Error(`Invalid m-value for interpolation: ${point1.m} <= ${mValue} <= ${point2.m}`);
    if (point1.m >= point2.m) throw Error('Invalid m-values for interpolation ends');
    const portion = (mValue - point1.m) / (point2.m - point1.m);
    return [interpolate(point1.x, point2.x, portion), interpolate(point1.y, point2.y, portion)];
}

function interpolate(value1: number, value2: number, portion: number): number {
    return value1 + (value2 - value1) * portion;
}

function square(x: number) {
    return x * x;
}

function distanceSquared(v: Point, w: Point) {
    return square(v.x - w.x) + square(v.y - w.y);
}

export function distToSegmentSquared(p: Point, start: Point, end: Point) {
    const l2 = distanceSquared(start, end);
    if (l2 == 0) return distanceSquared(p, start);
    let t = ((p.x - start.x) * (end.x - start.x) + (p.y - start.y) * (end.y - start.y)) / l2;
    t = Math.max(0, Math.min(1, t));
    return distanceSquared(p, {
        x: start.x + t * (end.x - start.x),
        y: start.y + t * (end.y - start.y),
    });
}
