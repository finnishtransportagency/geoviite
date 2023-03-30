import memCache from 'memory-cache';
import { TimeStamp } from 'common/common-model';
import { initialChangeTime } from 'store/track-layout-store';
import { toDate } from 'utils/date-utils';

export type Cache<TKey, TVal> = {
    get(key: TKey): TVal | null;
    put: (key: TKey, val: TVal) => void;
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

// Providing own API for better control but (for now)
// using "memory-cache" as an implementation
export function cache<TKey, TVal>(): Cache<TKey, TVal> {
    const cache = new memCache.Cache<TKey, TVal>();
    return {
        get: cache.get,
        put: (key: TKey, val: TVal) => cache.put(key, val),
        remove: (key: TKey) => cache.del(key),
    };
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
        return cache.get(key) ?? put(key, getter());
    };
    function getMany<TId>(
        changeTime: TimeStamp | null,
        ids: TId[],
        cacheKey: (id: TId) => TKey,
        getter: (ids: TId[]) => Promise<(id: TId) => TVal>,
    ): Promise<TVal[]> {
        setChangeTime(changeTime);
        const toFetch = ids.filter((id) => cache.get(cacheKey(id)) === null);
        if (toFetch.length > 0) {
            const getting = getter(ids);
            for (const id of toFetch) {
                void put(
                    cacheKey(id),
                    getting.then((got) => got(id)),
                );
            }
        }
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
