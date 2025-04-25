import { describe, expect, test } from '@jest/globals';
import { mapLazy, reuseListElements } from 'utils/array-utils';
import { expectDefined } from 'utils/type-utils';

describe('mapLazy', () => {
    test('returns original list if nothing changed', () => {
        const a = [1, 2, 3];
        expect(a).toBe(mapLazy(a, (x) => x));
    });

    test('implements mapping', () => {
        const a = [
            [1, 0],
            [2, 0],
            [3, 0],
        ];
        const r = [2, 1];
        const changed = mapLazy(a, (e) => (e[0] === 2 ? r : e));
        expect(changed[0]).toBe(a[0]);
        expect(changed[1]).toBe(r);
        expect(changed[2]).toBe(a[2]);
    });

    test('passes indices to the callback', () => {
        const a = [0, 1, 2, 3];
        expect(mapLazy(a, (_, i) => i)).toEqual([0, 1, 2, 3]);
    });
});

describe('reuseListElements', () => {
    test('can add elements', () => {
        expect(reuseListElements([1, 2, 3], [1, 2], (n) => n)).toStrictEqual([1, 2, 3]);
        expect(reuseListElements([1, 2, 3], [1, 2], (_) => 0)).toStrictEqual([1, 2, 3]);
    });

    test('can replace elements', () => {
        expect(reuseListElements([1, 2, 4], [1, 2, 3], (n) => n)).toStrictEqual([1, 2, 4]);
        expect(reuseListElements([1, 2, 4], [1, 2, 3], (_) => 0)).toStrictEqual([1, 2, 4]);
    });

    test('can remove elements', () => {
        expect(reuseListElements([1, 2], [1, 2, 3], (n) => n)).toStrictEqual([1, 2]);
        expect(reuseListElements([1, 2], [1, 2, 3], (_) => 0)).toStrictEqual([1, 2]);
    });

    test('returns original set if nothing changed', () => {
        const a = [1, 2, 3];
        const changed = reuseListElements(
            [1, 2, 3],
            a,
            (x) => x,
            (a, b) => a === b,
        );
        expect(changed).toBe(a);
    });

    test('returns original instances of unchanged elements', () => {
        const a = [
            [1, 0],
            [2, 0],
            [3, 0],
        ];
        const r = [2, 1];
        const changed = reuseListElements(
            [[1, 0], r, [3, 0]],
            a,
            (es) => expectDefined(es[0]),
            (as, bs) => as.every((e, i) => e === bs[i]),
        );

        expect(changed[0]).toBe(a[0]);
        expect(changed[1]).toBe(r);
        expect(changed[2]).toBe(a[2]);
    });

    test('checks equality with deep equality by default', () => {
        const a = [
            [1, 0],
            [2, 0],
            [3, 0],
        ];
        const r = [2, 1];
        const changed = reuseListElements([[1, 0], r, [3, 0]], a, (_) => 0);

        expect(changed[0]).toBe(a[0]);
        expect(changed[1]).toBe(r);
        expect(changed[2]).toBe(a[2]);
    });

    test('handles null and undefined elements, even with trivial extractKey', () => {
        const a = [[1, 0], null, [2, 0], undefined, null, [3, 0]];
        const r = [2, 1];
        const changed = reuseListElements([[1, 0], null, r, undefined, null, [3, 0]], a, (_) => 0);

        expect(changed[0]).toBe(a[0]);
        expect(changed[1]).toStrictEqual(a[1]);
        expect(changed[2]).toBe(r);
        expect(changed[3]).toStrictEqual(a[3]);
        expect(changed[4]).toStrictEqual(a[4]);
        expect(changed[5]).toBe(a[5]);
    });

    test('returns new list reusing instances, when newContent has duplicates but same length as oldElements', () => {
        const a = [[1], [2], [3], [4]];
        const r = [[1], [1], [2], [2]];
        const changed = reuseListElements(r, a, (_) => 0);
        expect(changed[0]).toBe(a[0]);
        expect(changed[1]).toBe(a[0]);
        expect(changed[2]).toBe(a[1]);
        expect(changed[3]).toBe(a[1]);
    });
});
