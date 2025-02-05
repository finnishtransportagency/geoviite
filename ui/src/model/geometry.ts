import { Polygon } from 'ol/geom';
import { Coordinate } from 'ol/coordinate';
import { Range, Srid } from 'common/common-model';
import { expectCoordinate } from 'utils/type-utils';

export enum CoordinateSystem {
    TM35FIN = 'TM35FIN',
}

export type Point = {
    x: number;
    y: number;
};

export type Dimensions = {
    width: number;
    height: number;
};

export type GeometryPoint = Point & {
    srid: Srid;
};

export type Line = {
    start: Point;
    end: Point;
};

export type BoundingBox = {
    x: Range<number>;
    y: Range<number>;
};

export type Rectangle = Polygon;

export function coordsToPoint(coords: Coordinate): Point {
    const [x, y] = expectCoordinate(coords);
    return {
        x,
        y,
    };
}

export function createPoint(x: number, y: number): Point {
    return {
        x: x,
        y: y,
    };
}

export function createLine(start: Point, end: Point): Line {
    return {
        start: start,
        end: end,
    };
}

export function combineBoundingBoxes(boxes: BoundingBox[]) {
    return {
        x: {
            min: Math.min(...boxes.map((b) => b.x.min)),
            max: Math.max(...boxes.map((b) => b.x.max)),
        },
        y: {
            min: Math.min(...boxes.map((b) => b.y.min)),
            max: Math.max(...boxes.map((b) => b.y.max)),
        },
    };
}

export function boundingBoxAroundPoints(points: Point[]): BoundingBox {
    return {
        x: {
            min: Math.min(...points.map((point) => point.x)),
            max: Math.max(...points.map((point) => point.x)),
        },
        y: {
            min: Math.min(...points.map((point) => point.y)),
            max: Math.max(...points.map((point) => point.y)),
        },
    };
}

export function expandBoundingBox(bbox: BoundingBox, amount: number): BoundingBox {
    return expandBoundingBoxXY(bbox, amount, amount);
}

export function multiplyBoundingBox(bbox: BoundingBox, multiplier: number): BoundingBox {
    return expandBoundingBoxXY(
        bbox,
        rangeLength(bbox.x) * multiplier,
        rangeLength(bbox.y) * multiplier,
    );
}

export function expandBoundingBoxXY(
    bbox: BoundingBox,
    xAmount: number,
    yAmount: number,
): BoundingBox {
    return {
        x: {
            min: bbox.x.min - xAmount / 2,
            max: bbox.x.max + xAmount / 2,
        },
        y: {
            min: bbox.y.min - yAmount / 2,
            max: bbox.y.max + yAmount / 2,
        },
    };
}

export const boundingBoxesIntersect = (bbox1: BoundingBox, bbox2: BoundingBox): boolean =>
    rangesIntersect(bbox1.x, bbox2.x) && rangesIntersect(bbox1.y, bbox2.y);

export const boundingBoxContains = (bbox: BoundingBox, point: Point): boolean =>
    rangeContainsExclusive(bbox.x, point.x) && rangeContainsExclusive(bbox.y, point.y);

// https://stackoverflow.com/questions/9043805/test-if-two-lines-intersect-javascript-function
// returns true if the line from (a,b)->(c,d) intersects with (p,q)->(r,s)
function intersects(
    a: number,
    b: number,
    c: number,
    d: number,
    p: number,
    q: number,
    r: number,
    s: number,
): boolean {
    const det = (c - a) * (s - q) - (r - p) * (d - b);
    if (det === 0) {
        return false;
    } else {
        const lambda = ((s - q) * (r - a) + (p - r) * (s - b)) / det;
        const gamma = ((b - d) * (r - a) + (c - a) * (s - b)) / det;
        return 0 < lambda && lambda < 1 && 0 < gamma && gamma < 1;
    }
}

export function linesIntersect(line1: Line, line2: Line): boolean {
    return intersects(
        line1.start.x,
        line1.start.y,
        line1.end.x,
        line1.end.y,
        line2.start.x,
        line2.start.y,
        line2.end.x,
        line2.end.y,
    );
}

export function boundingBoxIntersectsLine(bbox: BoundingBox, line: Line): boolean {
    return (
        boundingBoxContains(bbox, line.start) ||
        boundingBoxContains(bbox, line.end) ||
        linesIntersect(
            createLine(createPoint(bbox.x.min, bbox.y.min), createPoint(bbox.x.min, bbox.y.max)),
            line,
        ) ||
        linesIntersect(
            createLine(createPoint(bbox.x.max, bbox.y.min), createPoint(bbox.x.max, bbox.y.max)),
            line,
        ) ||
        linesIntersect(
            createLine(createPoint(bbox.x.min, bbox.y.min), createPoint(bbox.x.max, bbox.y.min)),
            line,
        ) ||
        linesIntersect(
            createLine(createPoint(bbox.x.min, bbox.y.max), createPoint(bbox.x.max, bbox.y.max)),
            line,
        )
    );
}

export const rangesIntersect = (range1: Range<number>, range2: Range<number>): boolean =>
    range1.min < range2.max && range1.max > range2.min;

export const rangesIntersectInclusive = (range1: Range<number>, range2: Range<number>): boolean =>
    range1.min <= range2.max && range1.max >= range2.min;

export const rangeContainsExclusive = (range: Range<number>, value: number): boolean =>
    range.min < value && range.max > value;

export const rangeContainsInclusive = (range: Range<number>, value: number): boolean =>
    range.min <= value && range.max >= value;

export const mergeRanges = (range1: Range<number>, range2: Range<number>): Range<number> => ({
    min: Math.min(range1.min, range2.min),
    max: Math.max(range1.max, range2.max),
});

export const centerForBoundingBox = (bbox: BoundingBox): Point => ({
    x: (bbox.x.min + bbox.x.max) / 2,
    y: (bbox.y.min + bbox.y.max) / 2,
});

export const boundingBoxScale = (source: BoundingBox, target: BoundingBox) => {
    const scaleX = rangeLength(target.x) / rangeLength(source.x);
    const scaleY = rangeLength(target.y) / rangeLength(source.y);
    return Math.max(scaleX, scaleY);
};

const rangeLength = (range: Range<number>) => range.max - range.min;
