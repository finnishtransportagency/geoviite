import { TimeStamp } from 'common/common-model';
import { expectDefined } from 'utils/type-utils';
import { objectEquals } from 'utils/object-utils';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
type EmptyObject = {};

export const EMPTY_ARRAY: never[] = [] as const;

export const first = <T>(arr: readonly T[]) => arr[0];
export const last = <T>(arr: readonly T[]) => arr[arr.length - 1];
export const init = <T>(arr: readonly T[]) => arr.slice(0, -1);

export const lastIndex = <T>(arr: readonly T[]) => arr.length - 1;

export function nonEmptyArray<T>(...values: Array<T | undefined>): T[] {
    return values.filter(filterNotEmpty);
}

export function filterNotEmpty<TValue>(value: TValue | undefined): value is TValue {
    return value !== undefined;
}

export function filterIn<T>(others: T[]): (item: T) => boolean {
    return filterByIdInOrNotIn(others, (id) => id, true);
}

export function filterNotIn<T>(others: T[]): (item: T) => boolean {
    return filterByIdNotIn(others, (id) => id);
}

export function filterByIdNotIn<T, Id>(
    others: Id[],
    getId: (thing: T) => Id,
): (item: T) => boolean {
    return filterByIdInOrNotIn(others, getId, false);
}

function filterByIdInOrNotIn<T, Id>(
    others: Id[],
    getId: (thing: T) => Id,
    yesIn: boolean,
): (item: T) => boolean {
    const othersSet = new Set(others);
    return (item: T) => yesIn === othersSet.has(getId(item));
}

/**
 * Usage:
 * persons.filter(filterUniqueById((person) => person.id))
 *
 * @param getId
 */
export function filterUniqueById<T, TId>(getId: (item: T) => TId): (item: T) => boolean {
    const seen: Set<TId> = new Set();
    return (item: T) => {
        const id = getId(item);
        const rv = !seen.has(id);
        seen.add(id);
        return rv;
    };
}

/**
 * Usage:
 * keys.filter(filterUnique)
 */
export function filterUnique<T>(item: T, index: number, array: T[]): boolean {
    return array.findIndex((item2) => item2 === item) === index;
}

export function flatten<T>(list: T[][]): T[] {
    return list.flatMap((subList) => subList);
}

export type Comparator<T> = (v1: T, v2: T) => number;

export function negComparator<T>(comparator: Comparator<T>): Comparator<T> {
    return (v1: T, v2: T) => comparator(v1, v2) * -1;
}

export function fieldComparator<
    T extends EmptyObject | undefined,
    S extends EmptyObject | undefined,
>(getter: (obj: T) => S): Comparator<T> {
    return (v1: T, v2: T) => compareByField(v1, v2, getter);
}

export function chunk<T>(array: T[], chunkSize: number): T[][] {
    if (chunkSize <= 0) return [array];

    const chunks: T[][] = [];
    for (let i = 0; i < array.length; i += chunkSize) {
        chunks.push(array.slice(i, i + chunkSize));
    }

    return chunks;
}

//Null and undefined values are considered "max"
export function timeStampComparator<T>(getter: (obj: T) => TimeStamp | undefined): Comparator<T> {
    return (v1: T, v2: T) => {
        const aTime = getter(v1);
        const bTime = getter(v2);

        if (!aTime && !bTime) return 0;
        if (!aTime) return 1;
        if (!bTime) return -1;

        return aTime < bTime ? -1 : aTime === bTime ? 0 : 1;
    };
}

export function multiFieldComparator<
    T extends (EmptyObject | undefined)[],
    S extends EmptyObject | undefined,
>(...getters: { [K in keyof T]: (obj: T[K]) => S }): (v1: T[number], v2: T[number]) => number {
    return (v1: T[number], v2: T[number]) => {
        return getters.reduce((previousComparisonResult, nextGetter) => {
            return previousComparisonResult !== 0
                ? previousComparisonResult
                : compareByField(v1, v2, nextGetter);
        }, 0);
    };
}

export function deduplicate<T>(items: T[]): T[] {
    return [...new Set(items).values()];
}

export function deduplicateById<T, TId>(items: T[], getItemId: (item: T) => TId): T[] {
    const x = new Map<TId, T>();
    items.forEach((item) => {
        const id = getItemId(item);
        if (!x.has(id)) {
            x.set(id, item);
        }
    });
    return [...x.values()];
}

export const compareByFields = <
    T extends EmptyObject | undefined,
    S extends EmptyObject | undefined,
>(
    s1: T,
    s2: T,
    ...getters: ((s: T) => S)[]
) =>
    getters.reduce(
        (previousValue, getter) =>
            previousValue === 0 ? compareByField(s1, s2, getter) : previousValue,
        0,
    );

export function compareByField<
    T extends EmptyObject | undefined,
    S extends EmptyObject | undefined,
>(v1: T, v2: T, getter: (obj: T) => S): number {
    const f1 = getter(v1);
    const f2 = getter(v2);

    return compare(f1, f2);
}

export function compare<T extends EmptyObject | undefined>(f1: T, f2: T): number {
    if (f1 === undefined && f2 === undefined) return 0;
    else if (f1 === undefined) return -1;
    else if (f2 === undefined) return 1;
    else if (f1 < f2) return -1;
    else if (f2 < f1) return 1;
    else return 0;
}

export function groupBy<T, K extends string | number>(
    array: T[],
    getKey: (item: T) => K,
): Record<K, T[]> {
    return array.reduce(
        (acc, item) => {
            (acc[getKey(item)] ||= []).push(item);
            return acc;
        },
        {} as Record<K, T[]>,
    );
}

/**
 * Returns true if items1 and items2 contain equal items, order does not matter.
 *
 * @param items1
 * @param items2
 * @param itemEqualsFunc optional item equality check function, uses "==" operator by default
 */
export function itemsEqual<T>(
    items1: T[] | undefined,
    items2: T[] | undefined,
    itemEqualsFunc?: (item1: T, item2: T) => boolean,
): boolean {
    return (
        items1 === items2 || // object equality
        (items1?.length === 0 && items2?.length === 0) || // empty arrays // contains equal items
        (items1 !== undefined &&
            items2 !== undefined &&
            items1.length === items2.length &&
            // a collection cannot have an item that does not exist in another
            !items1.some(
                (item1: T) =>
                    !items2.find((item2) =>
                        itemEqualsFunc ? itemEqualsFunc(item1, item2) : item1 === item2,
                    ),
            ))
    );
}

export function minOf<T>(values: T[], comparator: (v1: T, v2: T) => number): T | undefined {
    return values.reduce<T | undefined>((old, candidate, index) => {
        if (index === 0 || comparator(old as T, candidate) > 0) {
            return candidate;
        } else {
            return old;
        }
    }, undefined);
}

export function maxOf<T>(values: T[], comparator: (v1: T, v2: T) => number): T | undefined {
    return values.reduce<T | undefined>((old, candidate, index) => {
        if (index === 0 || comparator(old as T, candidate) < 0) {
            return candidate;
        } else {
            return old;
        }
    }, undefined);
}

export function sum(values: number[]): number {
    return values.reduce<number>((memo, value) => {
        return memo + value;
    }, 0);
}

export function avg(values: number[]): number {
    return values.length > 0 ? sum(values) / values.length : Number.NaN;
}

export function addIfExists<T>(originalCollection: T[], newValue: T | undefined): T[] {
    return newValue ? [...originalCollection, newValue] : [...originalCollection];
}

export function indexIntoMap<Id, Obj extends { id: Id }>(objs: Obj[]): Map<Id, Obj> {
    return objs.reduce((map, obj) => map.set(obj.id, obj), new Map());
}

export function minimumIndexBy<T, B>(objs: readonly T[], by: (obj: T) => B): number | undefined {
    if (objs.length === 0) {
        return undefined;
    }
    const values = objs.map((obj) => by(obj));
    let min = expectDefined(first(values));
    let minIndex = 0;
    values.forEach((value, index) => {
        if (value < min) {
            min = expectDefined(value);
            minIndex = index;
        }
    });
    return minIndex;
}

export function partitionBy<T>(list: T[], by: (item: T) => boolean): [T[], T[]] {
    return list.reduce(
        (acc, item) => {
            acc[by(item) ? 0 : 1].push(item);
            return acc;
        },
        [[], []] as [T[], T[]],
    );
}

export function findLastIndex<T, B>(objs: readonly T[], predicate: (obj: T) => B): number {
    const reverseIndex = [...objs].reverse().findIndex(predicate);
    return reverseIndex >= 0 ? objs.length - 1 - reverseIndex : -1;
}

export function findInsertionIndex<T>(
    things: readonly T[],
    isInsertBefore: (v: T) => boolean,
): number {
    const i = things.findIndex(isInsertBefore);
    return i === -1 ? things.length : i;
}

export function insertAtIndex<T>(things: readonly T[], thing: T, index: number): T[] {
    return [...things.slice(0, index), thing, ...things.slice(index)];
}

export const findById = <T extends { id: string }>(objs: T[], id: string): T | undefined =>
    objs.find((obj) => obj.id === id);

/**
 * Like Object.entries, but with the assumption that the argument doesn't contain any fields not mentioned in its
 * type (they are still output, but the output type doesn't know about them).
 */
export const objectEntries = <T extends object>(obj: T) =>
    Object.entries(obj) as {
        [K in keyof T]-?: [K, T[K]];
    }[keyof T][];

export type Primitive = string | boolean | number;

/**
 * Returns an array with newList's content, reusing existing instances from oldList as much as possible, and
 * returning oldList itself if there are no changes at all.
 *
 * @param newList Returned list will have the same content and ordering as this one.
 * @param oldList Returned list will retain instances from this list, where equal to an element of newContent.
 * @param extractKey Used as a hashCode(): Returning the same key for distinct elements is harmless.
 * @param equals Defaults to a deep equality check.
 */
export function reuseListElements<T>(
    newList: readonly T[],
    oldList: T[],
    extractKey: (e: T) => Primitive,
    equals: (a: T, b: T) => boolean = objectEquals,
): T[] {
    const oldInstances = indexIntoKeyedMapWithDuplicateCounts(oldList, extractKey, equals);

    let mustReturnNew = newList.length !== oldList.length;
    const newSet = newList.map((newElement, newIndex) => {
        const oldOnKey = oldInstances.get(extractKey(newElement));
        if (oldOnKey === undefined) {
            mustReturnNew = true;
            return newElement;
        } else {
            const index = oldOnKey.findIndex((oldElement) => equals(oldElement[0], newElement));
            if (index === -1) {
                mustReturnNew = true;
                return newElement;
            } else {
                const oldElement = expectDefined(oldOnKey[index]);
                const remainingDuplicates = --oldElement[1];
                if (
                    remainingDuplicates < 0 ||
                    (!mustReturnNew && oldElement[0] !== oldList[newIndex])
                ) {
                    mustReturnNew = true;
                }

                return oldElement[0];
            }
        }
    });

    return mustReturnNew ? newSet : oldList;
}

const indexIntoKeyedMapWithDuplicateCounts = <T>(
    elements: readonly T[],
    extractKey: (e: T) => Primitive,
    equals: (a: T, b: T) => boolean,
): Map<Primitive, [T, number][]> =>
    elements.reduce(
        (map, element) => addToMapWithDuplicateCount(extractKey, equals, map, element),
        new Map(),
    );

function addToMapWithDuplicateCount<T>(
    extractKey: (e: T) => Primitive,
    equals: (a: T, b: T) => boolean,
    map: Map<Primitive, [T, number][]>,
    obj: T,
): Map<Primitive, [T, number][]> {
    const key = extractKey(obj);
    if (map.has(key)) {
        const onKey = expectDefined(map.get(key));
        const index = onKey.findIndex((elementOnKey) => equals(obj, elementOnKey[0]));
        if (index === -1) {
            onKey.push([obj, 1]);
        } else {
            expectDefined(onKey[index])[1]++;
        }
    } else {
        map.set(key, [[obj, 1]]);
    }
    return map;
}

/**
 * map the given transform() function over the list, but if no element actually changed (by triple-equals), return
 * the original list instance.
 */
export function mapLazy<T>(list: T[], transform: (element: T, index: number) => T): T[] {
    let changed = false;
    const newList = list.map((element, index) => {
        const result = transform(element, index);
        if (result !== element) {
            changed = true;
        }
        return result;
    });
    return changed ? newList : list;
}

export function takeLast<T>(list: T[], count: number): T[] {
    return list.slice(-count);
}
