import memCache from 'memory-cache';
import { TimeStamp } from 'common/common-model';
import { initialChangeTime } from 'track-layout/track-layout-store';
import { toDate } from 'utils/date-utils';

export type Cache<TKey, TVal> = {
    get(key: TKey): TVal | null;
    put: (key: TKey, val: TVal) => void;
    remove: (key: TKey) => void;
};

export type AsyncCache<TKey, TVal> = {
    getImmutable(key: TKey, getter: () => Promise<TVal>): Promise<TVal>;
    get(changeTime: TimeStamp, key: TKey, getter: () => Promise<TVal>): Promise<TVal>;
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
    const getCached = (changeTime: TimeStamp | null, key: TKey, getter: () => Promise<TVal>) => {
        if (changeTime != null) {
            const newChangeTime = toDate(changeTime);
            if (newChangeTime > ownChangeTime) {
                ownChangeTime = newChangeTime;
                cache.clear();
            }
        }
        const existingPromise = cache.get(key);
        if (existingPromise != null) {
            return existingPromise;
        } else {
            const newPromise = getter();
            cache.put(key, newPromise);
            newPromise.catch(() => cache.del(key)); // Remove failed results from cache
            return newPromise;
        }
    };
    return {
        getImmutable: (key: TKey, getter: () => Promise<TVal>) => getCached(null, key, getter),
        get: (changeTime: TimeStamp, key: TKey, getter: () => Promise<TVal>) =>
            getCached(changeTime, key, getter),
        remove: (key: TKey) => cache.del(key),
    };
}
