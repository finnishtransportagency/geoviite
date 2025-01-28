import { describe, expect, test } from '@jest/globals';
import { getMissingCoveringRange, weaveKms } from '../../src/vertical-geometry/km-heights-api';

describe('weave', () =>
    test('combines sequences of closed ranges', () => {
        testWeave([[1, 5]], [4, 7], [[1, 7]]);
        testWeave([], [4, 7], [[4, 7]]);
        testWeave([[1, 4]], [5, 6], [[1, 6]]);
        testWeave(
            [[1, 2]],
            [4, 5],
            [
                [1, 2],
                [4, 5],
            ],
        );
        testWeave(
            [
                [3, 4],
                [6, 7],
            ],
            [1, 12],
            [[1, 12]],
        );
        testWeave(
            [
                [1, 2],
                [8, 9],
            ],
            [4, 5],
            [
                [1, 2],
                [4, 5],
                [8, 9],
            ],
        );
    }));

describe('getMissingCoveringRange', () => {
    test("describes a single range to cover everything that's missing from a query in a sequence ranges", () => {
        testCoveringRange(
            [
                [1, 2],
                [2, 3],
            ],
            [1, 5],
            [3, 5],
        );
        testCoveringRange([], [4, 5], [4, 5]);
        testCoveringRange(
            [
                [1, 5],
                [5, 7],
            ],
            [4, 7],
            undefined,
        );
        testCoveringRange(
            [
                [1, 4],
                [7, 8],
                [8, 10],
            ],
            [2, 9],
            [4, 7],
        );
        testCoveringRange(
            [
                [1, 4],
                [8, 10],
            ],
            [0, 11],
            [0, 11],
        );
    });
});

function testCoveringRange(
    resolvedRanges: [number, number][],
    queryRange: [number, number],
    expectedRange: [number, number] | undefined,
) {
    const actual = getMissingCoveringRange(resolvedRanges, queryRange[0], queryRange[1]);
    if (expectedRange === null) {
        expect(actual).toBeUndefined();
    } else {
        expect(actual).toEqual(expectedRange);
    }
}

function testWeave(
    resolvedRanges: [number, number][],
    loadedRange: [number, number],
    expectedRanges: [number, number][],
) {
    const resolved = manyClosedRanges(resolvedRanges);
    const expected = manyClosedRanges(expectedRanges);

    expect(weaveKms((x) => x, resolved, closedRange(loadedRange[0], loadedRange[1]))).toEqual(
        expected,
    );
}

function manyClosedRanges(ranges: [number, number][]) {
    return ranges.reduce((acc, r) => {
        acc.push(...closedRange(r[0], r[1]));
        return acc;
    }, [] as number[]);
}

function closedRange(from: number, to: number): number[] {
    return [...Array(to - from + 1).keys()].map((n) => n + from);
}
