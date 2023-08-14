import memCache from 'memory-cache';
import { TimeStamp } from 'common/common-model';
import { toDate } from 'utils/date-utils';
import { initialChangeTime } from 'common/common-slice';
import { chunk } from 'utils/array-utils';

export type Cache<TKey, TVal> = {
    get(key: TKey): TVal | null;
    put: (key: TKey, val: TVal) => void;
    getOrCreate: (key: TKey, createNew: () => TVal) => TVal;
    remove: (key: TKey) => void;
};

export type AsyncCache<TKey, TVal> = {
    getImmutable(key: TKey, getter: () => Promise<TVal>): Promise<TVal>;
    get(changeTime: TimeStamp, key: TKey, getter: () => Promise<TVal>): Promise<TVal>;
    getMany<TId>(
        changeTime: TimeStamp,
        ids: TId[],
        cacheKey: (id: TId) => TKey,
        getter: (ids: TId[]) => Promise<(id: TId) => TVal>,
    ): Promise<TVal[]>;
    remove: (key: TKey) => void;
};

export function cache<TKey, TVal>(maxSize?: number): Cache<TKey, TVal> {
    const items = new Map<TKey, TVal>();
    const touched = new Map<TKey, number>();
    let hitCount = 0;
    let missCount = 0;

    const cache = {
        get: (key: TKey) => items.get(key) || null,
        getOrCreate: (key: TKey, createNew: () => TVal) => {
            const val = cache.get(key);
            if (val != null) {
                hitCount++;
                return val;
            }
            missCount++;
            const newVal = createNew();
            cache.put(key, newVal);
            return newVal;
        },
        put: (key: TKey, val: TVal) => {
            while (maxSize != undefined && items.size > maxSize) {
                // remove portion of the items
                cache.removeOldItems(Math.ceil(maxSize * 0.1));
            }
            items.set(key, val);
            touched.set(key, new Date().getTime());
        },
        removeOldItems: (count: number) => {
            const sortedPairs = [...touched].sort((pair1, pair2) => pair1[1] - pair2[1]);
            sortedPairs.slice(0, count).forEach((pair) => cache.remove(pair[0]));
        },
        remove: (key: TKey) => {
            items.delete(key);
            touched.delete(key);
        },
        getDebugInfo: () => {
            return {
                size: items.size,
                maxSize: maxSize,
                hitCount: hitCount,
                missCount: missCount,
            };
        },
    };
    return cache;
}

export function asyncCache<TKey, TVal>(): AsyncCache<TKey, TVal> {
    const cache = new memCache.Cache<TKey, Promise<TVal>>();
    let ownChangeTime = toDate(initialChangeTime);
    const put = (key: TKey, promise: Promise<TVal>): Promise<TVal> => {
        void cache.put(key, promise);
        promise.catch(() => cache.del(key)); // Remove failed results from cache
        return promise;
    };
    const getCached = (changeTime: TimeStamp | null, key: TKey, getter: () => Promise<TVal>) => {
        setChangeTime(changeTime);
        // TODO: GVT-2014 some caches have null as a valid value -> should not cause re-fetch
        return cache.get(key) ?? put(key, getter());
    };
    function getMany<TId>(
        changeTime: TimeStamp | null,
        ids: TId[],
        cacheKey: (id: TId) => TKey,
        getter: (ids: TId[]) => Promise<(id: TId) => TVal>,
    ): Promise<TVal[]> {
        setChangeTime(changeTime);
        // TODO: GVT-2014 some caches have null as a valid value -> should not cause re-fetch
        const fetchIds = ids.filter((id) => cache.get(cacheKey(id)) === null);

        chunk(fetchIds, 50).forEach((chunkIds) => {
            const getting = getter(chunkIds);
            for (const id of chunkIds) {
                void put(
                    cacheKey(id),
                    getting.then((gotFn) => gotFn(id)),
                );
            }
        });

        // type coercion safety: we called put for every missing cache key
        return Promise.all(ids.map((id) => cache.get(cacheKey(id))) as TVal[]);
    }

    function setChangeTime(changeTime: TimeStamp | null) {
        if (changeTime != null) {
            const newChangeTime = toDate(changeTime);
            if (newChangeTime > ownChangeTime) {
                ownChangeTime = newChangeTime;
                cache.clear();
            }
        }
    }

    return {
        getImmutable: (key: TKey, getter: () => Promise<TVal>) => getCached(null, key, getter),
        get: (changeTime: TimeStamp, key: TKey, getter: () => Promise<TVal>) =>
            getCached(changeTime, key, getter),
        remove: (key: TKey) => cache.del(key),
        getMany,
    };
}
