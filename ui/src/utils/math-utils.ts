import { Point } from 'model/geometry';

export function directionBetweenPoints(p1: Point, p2: Point) {
    return Math.atan2(p2.y - p1.y, p2.x - p1.x);
}

export function angleDiffRads(rads1: number, rads2: number): number {
    const diff = normalizeRads(rads2 - rads1);
    return diff > Math.PI ? 2 * Math.PI - diff : diff;
}

function normalizeRads(rads: number) {
    return normalizeToRangeNonInclusive(rads, 2 * Math.PI);
}

function normalizeToRangeNonInclusive(value: number, range: number): number {
    return value < 0 || value >= range ? value - range * Math.floor(value / range) : value;
}
