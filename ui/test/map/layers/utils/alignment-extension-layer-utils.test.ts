import { describe, expect, test } from '@jest/globals';
import {
    endLocation,
    extensionLocation,
    nearestAlignmentEnd,
} from 'map/layers/utils/alignment-extension-layer-utils';
import {
    AlignmentPoint,
    AlignmentStartAndEnd,
    EndpointType,
} from 'track-layout/track-layout-model';
import { createPoint, Point } from 'model/geometry';

const point = (x: number, y: number, m: number): AlignmentPoint => ({ x, y, m });

const startAndEnd = (
    start: AlignmentPoint | undefined,
    end: AlignmentPoint | undefined,
    startDirection = 0,
    endDirection = 0,
): AlignmentStartAndEnd =>
    ({
        id: 'test',
        start: start && { point: start, direction: startDirection },
        end: end && { point: end, direction: endDirection },
    }) as AlignmentStartAndEnd;

// An alignment running along the X axis from (0,0) to (100,0); direction of travel is +x at both ends.
const alignment = startAndEnd(point(0, 0, 0), point(100, 0, 100));

const nearestEnd = (sae: AlignmentStartAndEnd | undefined, to: Point): EndpointType | undefined =>
    nearestAlignmentEnd(sae, to)?.end;

describe('nearestAlignmentEnd', () => {
    test('picks the start when the cursor is closer to it', () => {
        expect(nearestEnd(alignment, createPoint(10, 30))).toEqual('START');
    });

    test('picks the end when the cursor is closer to it', () => {
        expect(nearestEnd(alignment, createPoint(90, 30))).toEqual('END');
    });

    test('flips from start to end as the cursor crosses the midpoint', () => {
        expect(nearestEnd(alignment, createPoint(49, 5))).toEqual('START');
        expect(nearestEnd(alignment, createPoint(51, 5))).toEqual('END');
    });

    test('measures distance in two dimensions', () => {
        // A diagonal alignment, so that neither coordinate alone decides which end is nearer.
        const diagonal = startAndEnd(point(0, 0, 0), point(100, 100, 142));
        expect(nearestEnd(diagonal, createPoint(0, 90))).toEqual('START');
        expect(nearestEnd(diagonal, createPoint(90, 100))).toEqual('END');
    });

    test('reports the outward direction, which points away from the track at both ends', () => {
        // Travel direction is +x at both ends, so extending from the end goes +x and from the
        // start goes -x.
        expect(nearestAlignmentEnd(alignment, createPoint(90, 0))?.outwardDirection).toBeCloseTo(0);
        expect(nearestAlignmentEnd(alignment, createPoint(10, 0))?.outwardDirection).toBeCloseTo(
            Math.PI,
        );
    });

    test('uses the only available end when the alignment has just one', () => {
        const onlyEnd = startAndEnd(undefined, point(100, 0, 100));
        expect(nearestEnd(onlyEnd, createPoint(0, 0))).toEqual('END');
    });

    test('is undefined when there is nothing to anchor to', () => {
        expect(nearestAlignmentEnd(undefined, createPoint(0, 0))).toBeUndefined();
        expect(
            nearestAlignmentEnd(startAndEnd(undefined, undefined), createPoint(0, 0)),
        ).toBeUndefined();
    });
});

describe('extensionLocation', () => {
    const end = nearestAlignmentEnd(alignment, createPoint(90, 0))!;
    const start = nearestAlignmentEnd(alignment, createPoint(10, 0))!;

    const expectPointCloseTo = (actual: Point, expected: Point) => {
        expect(actual.x).toBeCloseTo(expected.x);
        expect(actual.y).toBeCloseTo(expected.y);
    };

    test('returns the raw cursor when snapping is off', () => {
        expect(extensionLocation(end, createPoint(150, 40), false)).toEqual(createPoint(150, 40));
    });

    test('projects the cursor onto the outward ray when snapping is on', () => {
        // End at (100,0) extending +x: the 40 m of lateral offset is dropped.
        expectPointCloseTo(extensionLocation(end, createPoint(150, 40), true), createPoint(150, 0));
    });

    test('extends the start end in its own outward (-x) direction', () => {
        expectPointCloseTo(
            extensionLocation(start, createPoint(-30, 20), true),
            createPoint(-30, 0),
        );
    });

    test('clamps to the alignment end rather than folding back over the track', () => {
        // Cursor lies back along the track from the end, so the projection would be negative.
        expectPointCloseTo(extensionLocation(end, createPoint(80, 40), true), createPoint(100, 0));
    });
});

describe('endLocation', () => {
    test('resolves each endpoint type to its point', () => {
        expect(endLocation(alignment, 'START')).toEqual(point(0, 0, 0));
        expect(endLocation(alignment, 'END')).toEqual(point(100, 0, 100));
    });

    test('is undefined when the requested end is missing', () => {
        expect(endLocation(startAndEnd(undefined, point(100, 0, 100)), 'START')).toBeUndefined();
        expect(endLocation(undefined, 'END')).toBeUndefined();
    });
});
