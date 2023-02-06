import { TimeStamp } from 'common/common-model';

export function nonEmptyArray<T>(...values: Array<T | null | undefined>): T[] {
    return values.filter(filterNotEmpty);
}

export function filterNotEmpty<TValue>(value: TValue | null | undefined): value is TValue {
    return value !== null && value !== undefined;
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
    return array.findIndex((item2) => item2 == item) == index;
}

export function flatten<T>(list: T[][]): T[] {
    return list.flatMap((subList) => subList);
}

export function negComparator<T>(comparator: (v1: T, v2: T) => number): (v1: T, v2: T) => number {
    return (v1: T, v2: T) => comparator(v1, v2) * -1;
}

export function fieldComparator<T, S>(getter: (obj: T) => S): (v1: T, v2: T) => number {
    return (v1: T, v2: T) => compareByField(v1, v2, getter);
}

//Null and undefined values are considered "max"
export function timeStampComparator<T>(
    getter: (obj: T) => TimeStamp | undefined | null,
): (v1: T, v2: T) => number {
    return (v1: T, v2: T) => {
        const aTime = getter(v1);
        const bTime = getter(v2);

        if (!aTime && !bTime) return 0;
        if (!aTime) return 1;
        if (!bTime) return -1;

        return aTime < bTime ? -1 : aTime == bTime ? 0 : 1;
    };
}

export function multiFieldComparator<T, S>(
    ...getters: ((obj: T) => S)[]
): (v1: T, v2: T) => number {
    return (v1: T, v2: T) => {
        for (const getter of getters) {
            const comp = compareByField(v1, v2, getter);
            if (comp != 0) return comp;
        }
        return 0;
    };
}

export function deduplicate<T>(items: T[]): T[] {
    return [...new Set(items).values()];
}

export function deduplicateById<T, TId>(items: T[], getItemId: (item: T) => TId): T[] {
    return items.filter((item, index) => {
        const id = getItemId(item);
        return items.findIndex((item2) => getItemId(item2) == id) == index;
    });
}

export function arraysEqual<T>(arr1: T[], arr2: T[]) {
    return JSON.stringify(arr1) === JSON.stringify(arr2);
}

export const compareByFields = <T, S>(s1: T, s2: T, ...getters: ((s: T) => S)[]) =>
    getters.reduce(
        (previousValue, getter) =>
            previousValue === 0 ? compareByField(s1, s2, getter) : previousValue,
        0,
    );

export function compareByField<T, S>(v1: T, v2: T, getter: (obj: T) => S): number {
    const f1 = getter(v1);
    const f2 = getter(v2);

    return compare(f1, f2);
}

export function compare<T>(f1: T, f2: T): number {
    if (f1 == null && f2 == null) return 0;
    else if (f1 == null) return -1;
    else if (f2 == null) return 1;
    else if (f1 < f2) return -1;
    else if (f2 < f1) return 1;
    else return 0;
}

export function groupBy<T, K extends string | number>(array: T[], getKey: (item: T) => K) {
    return array.reduce((acc, item) => {
        (acc[getKey(item)] ||= []).push(item);
        return acc;
    }, {} as Record<K, T[]>);
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
        (items1?.length == 0 && items2?.length == 0) || // empty arrays // contains equal items
        (items1 != undefined &&
            items2 != undefined &&
            items1.length === items2.length &&
            // a collection cannot have an item that does not exist in another
            !items1.some(
                (item1: T) =>
                    !items2.find((item2) =>
                        itemEqualsFunc ? itemEqualsFunc(item1, item2) : item1 == item2,
                    ),
            ))
    );
}

export function maxOf<T>(values: T[], comparator: (v1: T, v2: T) => number): T | undefined {
    return values.reduce<T | undefined>((old, candidate, index) => {
        if (index == 0 || comparator(old as T, candidate) < 0) {
            return candidate;
        } else {
            return old;
        }
    }, undefined);
}

export function first<T>(array: T[]): T {
    return array[0];
}

export function last<T>(array: T[]): T {
    return array[array.length - 1];
}

export function subtract<T>(originalCollection: T[], valuesToRemove: T[]): T[] {
    if (valuesToRemove.length === 0) {
        return originalCollection;
    }
    const setToRemove = new Set(valuesToRemove);
    return originalCollection.filter((x) => !setToRemove.has(x));
}

export function removeIfExists<T>(originalCollection: T[], valueToRemove: T | undefined) {
    return valueToRemove
        ? originalCollection.filter((changeId) => changeId != valueToRemove)
        : originalCollection;
}

export function addIfExists<T>(originalCollection: T[], newValue: T | undefined): T[] {
    return newValue ? [...originalCollection, newValue] : [...originalCollection];
}
