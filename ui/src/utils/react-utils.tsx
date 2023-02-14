import * as React from 'react';
import { ForwardedRef, useEffect, useRef, useState } from 'react';
import { debounce } from 'ts-debounce';

/**
 * To load/get something asynchronously and to set that into state
 *
 * Usage (in React component):
 * const something = useLoader(() => getSomething(someIdMaybe), [someIdMaybe]);
 *
 * Last param contains dependencies, like in useEffect.
 *
 * @param loadFunc
 * @param deps
 */
export function useLoader<TEntity>(
    loadFunc: () => Promise<TEntity> | undefined,
    deps: unknown[],
): TEntity | undefined {
    return useLoaderWithStatus(loadFunc, deps)[0];
}

export function useNullableLoader<TEntity>(
    loadFunc: () => Promise<TEntity | null> | undefined,
    deps: unknown[],
): TEntity | undefined {
    const nullMappingLoadFunc = () => loadFunc()?.then((r) => (r === null ? undefined : r));
    return useLoader(nullMappingLoadFunc, deps);
}

export enum LoaderStatus {
    Initialized,
    Loading,
    Ready,
    Cancelled,
}

/**
 * To load/get something asynchronously and to set that into state.
 * This version of useLoader provides a loader status as well.
 *
 * Usage (in React component):
 * const [something, loadStatusForSomething] = useLoaderWithStatus(
 *     () => getSomething(someIdMaybe),
 *     [someIdMaybe]
 * );
 *
 * Last param contains dependencies, like in useEffect.
 *
 * @param loadFunc
 * @param deps
 */
export function useLoaderWithStatus<TEntity>(
    loadFunc: () => Promise<TEntity> | undefined,
    deps: unknown[],
): [TEntity | undefined, LoaderStatus] {
    const [entity, setEntity] = React.useState<TEntity>();
    const [loaderStatus, setLoaderStatus] = React.useState<LoaderStatus>(LoaderStatus.Initialized);
    React.useEffect(() => {
        const result = loadFunc();
        let cancel = false;
        if (result) {
            setLoaderStatus(LoaderStatus.Loading);
            result.then((r) => {
                if (!cancel) {
                    setEntity(r);
                    setLoaderStatus(LoaderStatus.Ready);
                }
            });
        } else setEntity(undefined);

        return () => {
            cancel = true;
            setLoaderStatus(LoaderStatus.Cancelled);
        };
    }, deps);
    return [entity, loaderStatus];
}

export function useLoaderWithTimer<TEntity>(
    setEntity: (entity: TEntity | undefined) => void,
    loadFunc: () => Promise<TEntity> | undefined,
    deps: unknown[],
    timeout: number,
) {
    React.useEffect(() => {
        let cancel = false;
        setEntity(undefined);
        function fetchEntities() {
            const result = loadFunc();
            if (result) {
                result.then((r) => {
                    if (!cancel) setEntity(r);
                });
            }
        }
        fetchEntities();
        const intervalTimer = setInterval(fetchEntities, timeout);
        return () => {
            cancel = true;
            clearInterval(intervalTimer);
        };
    }, deps);
}

/**
 * Usage:
 *
 * const [searchTerm, setSearchTerm] = React.useState('');
 * const debouncedSearchTerm = useDebouncedSate(searchTerm, 250);
 *
 * const items = useLoader(() => searchItems(debouncedSearchTerm), [debouncedSearchTerm]);
 *
 * <input value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)}/>
 *
 * In this example "searchItems" will be called after 250 millisecond is passed from the last
 * change of the "searchTerm".
 *
 * @param value value to set
 * @param delay in millis
 */
export function useDebouncedState<TValue>(value: TValue, delay: number): TValue | undefined {
    const [debouncedValue, setDebouncedValue] = React.useState<TValue>();
    const setValue = React.useCallback(
        debounce((val: TValue) => setDebouncedValue(val), delay),
        [],
    );
    setValue(value);
    return debouncedValue;
}

// https://github.com/facebook/react/issues/24722
export function useCloneRef<T>(
    ref: ForwardedRef<T>,
    initialValue: T | null = null,
): React.MutableRefObject<T | null> {
    const localRef = useRef<T>(initialValue);
    useEffect(() => {
        if (ref) {
            if (typeof ref === 'function') {
                ref(localRef.current);
            } else {
                ref.current = localRef.current;
            }
        }
    }, [ref]);
    return localRef;
}

export function useMapState<K, V>(
    initial?: Map<K, V> | (() => Map<K, V>),
): [
        map: Map<K, V>,
        setValue: (key: K, value: V) => void,
        removeKey: (key: K) => void,
        setMap: React.Dispatch<React.SetStateAction<Map<K, V>>>,
    ] {
    const [map, setMap] = useState<Map<K, V>>(initial ?? (() => new Map()));
    const setValue = (key: K, value: V) => setMap((prevMap) => new Map(prevMap).set(key, value));
    const removeKey = (key: K) =>
        setMap((prevMap) => {
            const copy = new Map(prevMap);
            copy.delete(key);
            return copy;
        });
    return [map, setValue, removeKey, setMap];
}
export function useSetState<T>(
    initial?: Set<T> | (() => Set<T>),
): [
        set: Set<T>,
        addToSet: (member: T) => void,
        deleteFromSet: (member: T) => void,
        setSet: React.Dispatch<React.SetStateAction<Set<T>>>,
    ] {
    const [set, setSet] = useState<Set<T>>(initial ?? (() => new Set()));
    const addToSet = (member: T) => setSet((prevSet) => new Set(prevSet).add(member));
    const deleteFromSet = (member: T) =>
        setSet((prevSet) => {
            const copy = new Set(prevSet);
            copy.delete(member);
            return copy;
        });
    return [set, addToSet, deleteFromSet, setSet];
}
