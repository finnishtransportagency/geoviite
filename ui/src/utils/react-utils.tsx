import * as React from 'react';
import { DependencyList, EffectCallback, ForwardedRef, useEffect, useRef, useState } from 'react';
import { debounce } from 'ts-debounce';
import { ValueOf } from './type-utils';

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

export function useOptionalLoader<TEntity>(
    loadFunc: () => Promise<TEntity | undefined> | undefined,
    deps: unknown[],
): TEntity | undefined {
    const nilMappingLoadFunc = () => loadFunc()?.then((r) => (r === undefined ? undefined : r));
    return useLoader(nilMappingLoadFunc, deps);
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
    return useRateLimitedLoaderWithStatus(loadFunc, 0, deps);
}

function dependenciesAreEqual(deps1: unknown[], deps2: unknown[]): boolean {
    // React uses "Object.is" to compare dependencies, so this should return equal result
    return deps1.length === deps2.length && deps1.every((d1, index) => Object.is(d1, deps2[index]));
}

const initialDeps: unknown[] = [];

function dependenciesAreChangedInState(deps: unknown[]): boolean {
    const [depsInState, setDepsInState] = React.useState(initialDeps);
    if (depsInState === initialDeps || !dependenciesAreEqual(deps, depsInState)) {
        setDepsInState(deps);
        return true;
    }
    return false;
}

export function useRateLimitedLoaderWithStatus<TEntity>(
    loadFunc: () => Promise<TEntity> | undefined,
    minWaitTime: number,
    deps: unknown[],
): [TEntity | undefined, LoaderStatus] {
    const [entity, setEntity] = React.useState<TEntity>();
    const [loaderStatusInState, setLoaderStatusInState] = React.useState<LoaderStatus>(
        LoaderStatus.Initialized,
    );
    const depsAreChanged = dependenciesAreChangedInState(deps);

    useRateLimitedEffect(
        () => {
            const result = loadFunc();
            let cancel = false;
            if (result) {
                setLoaderStatusInState(LoaderStatus.Loading);
                result
                    .then((r) => {
                        if (!cancel) {
                            setEntity(r);
                            setLoaderStatusInState(LoaderStatus.Ready);
                        }
                    })
                    .catch((e) => console.log('loader promise rejected', e));
            } else {
                setEntity(undefined);
                setLoaderStatusInState(LoaderStatus.Ready);
            }

            return () => {
                cancel = true;
                setLoaderStatusInState(LoaderStatus.Cancelled);
            };
        },
        minWaitTime,
        deps,
    );

    // If "useLoader" is called with modified dependencies, actual "load" function will be called
    // asynchronously and status will then be set to "loading", but it is more precise to set and
    // return "initialize" status synchronously.
    const loaderStatus = depsAreChanged ? LoaderStatus.Initialized : loaderStatusInState;
    if (loaderStatus != loaderStatusInState) {
        setLoaderStatusInState(loaderStatus);
    }

    return [entity, loaderStatus];
}

export function useImmediateLoader<TEntity>(setter: (result: TEntity) => void): {
    isLoading: boolean;
    load: (promise: Promise<TEntity>) => void;
} {
    const [isLoading, setIsLoading] = useState(false);
    const query = useRef({ cancel: false });
    return {
        isLoading,
        load: (promise) => {
            setIsLoading(true);
            query.current.cancel = true;
            const nextQuery = {
                cancel: false,
            };
            query.current = nextQuery;
            promise
                .then((result) => nextQuery.cancel || setter(result))
                .finally(() => nextQuery.cancel || setIsLoading(false));
        },
    };
}

function useTwoPartEffectWithHook<TEntity>(
    useEffect: (effect: EffectCallback, deps?: DependencyList) => void,
    loadFunc: () => Promise<TEntity> | undefined,
    onceOnFulfilled: (result: TEntity) => void,
    deps: unknown[],
): LoaderStatus {
    const [loaderStatus, setLoaderStatus] = React.useState<LoaderStatus>(LoaderStatus.Initialized);

    useEffect(() => {
        const promise = loadFunc();
        let cancelled = false;

        if (promise) {
            setLoaderStatus(LoaderStatus.Loading);

            promise
                .then((r) => {
                    if (!cancelled) {
                        setLoaderStatus(LoaderStatus.Ready);

                        onceOnFulfilled(r);
                    }
                })
                .catch((e) => console.log('loader promise rejected', e));
        }

        return () => {
            cancelled = true;
            setLoaderStatus(LoaderStatus.Cancelled);
        };
    }, deps);

    return loaderStatus;
}

/**
 * Load/fetch something asynchronously and, if the load finishes, call the given onceOnFulfilled callback with its
 * result.
 */
export function useTwoPartEffect<TEntity>(
    loadFunc: () => Promise<TEntity> | undefined,
    onceOnFulfilled: (result: TEntity) => void,
    deps: unknown[],
): LoaderStatus {
    return useTwoPartEffectWithHook(useEffect, loadFunc, onceOnFulfilled, deps);
}

export function useRateLimitedTwoPartEffect<TEntity>(
    loadFunc: () => Promise<TEntity> | undefined,
    onceOnFulfilled: (result: TEntity) => void,
    waitBetweenCalls: number,
    deps: unknown[],
): LoaderStatus {
    return useTwoPartEffectWithHook(
        (cb, deps) => useRateLimitedEffect(cb, waitBetweenCalls, deps),
        loadFunc,
        onceOnFulfilled,
        deps,
    );
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
                result
                    .then((r) => {
                        if (!cancel) setEntity(r);
                    })
                    .catch((e) => console.log('loader promise rejected', e));
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

/**
 * Run an effect at most waitBetweenCalls milliseconds apart, and if there have been changes to the dependencies within
 * the waiting period, once after that period has passed. If waitBetweenCalls = 0, behaves as useEffect.
 */
export function useRateLimitedEffect(
    effect: EffectCallback,
    waitBetweenCalls: number,
    deps?: DependencyList,
): void {
    const lastFireTime = useRef<number>();
    const nextWakeup = useRef<ReturnType<typeof setTimeout>>();
    const lastDestructor = useRef<void | (() => void)>();

    useEffect(() => {
        // always reset the next wakeup: If we are firing the effect, then we don't want to keep the wakeup around,
        // and if we're delaying, we still want the last version of the effect
        if (nextWakeup.current !== undefined) {
            clearTimeout(nextWakeup.current);
            nextWakeup.current = undefined;
        }

        const now = Date.now();
        if (
            lastFireTime.current === undefined ||
            now < lastFireTime.current || // if time turned back, just run the effect and roll with it
            now - lastFireTime.current >= waitBetweenCalls
        ) {
            lastFireTime.current = now;
            return effect();
        } else {
            nextWakeup.current = setTimeout(
                () => {
                    lastFireTime.current = Date.now();
                    lastDestructor.current = effect();
                    nextWakeup.current = undefined;
                },
                waitBetweenCalls - (now - lastFireTime.current),
            );
            return () => {
                if (lastDestructor.current !== undefined) {
                    lastDestructor.current();
                    lastDestructor.current = undefined;
                }
            };
        }
    }, deps);
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

type PropsType = Record<string, unknown>;

export function useTraceProps(componentName: string, props: PropsType) {
    const prev = useRef(props);

    useEffect(() => {
        const changedProps = Object.entries(props).reduce(
            (acc, [k, v]) => {
                if (prev.current[k] !== v) {
                    acc[k] = { old: prev.current[k], new: v };
                }

                return acc;
            },
            {} as { [key: string]: { old: ValueOf<PropsType>; new: ValueOf<PropsType> } },
        );

        if (Object.keys(changedProps).length > 0) {
            console.log(`[${componentName}] Changed props:`, changedProps);
        }

        prev.current = props;
    });
}
