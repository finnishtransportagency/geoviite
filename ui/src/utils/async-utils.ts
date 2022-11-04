import { debounce, Options } from 'ts-debounce';

export type AsyncProcedure = (...args: unknown[]) => Promise<unknown>;

export interface DebouncedAsyncFunction<F extends AsyncProcedure> {
    (this: ThisParameterType<F>, ...args: Parameters<F>): ReturnType<F>;

    cancel: (reason?: unknown) => void;
}

export function debounceAsync<AsyncF extends AsyncProcedure>(
    f: AsyncF,
    waitMilliseconds?: number,
    options?: Options<ReturnType<AsyncF>>,
): DebouncedAsyncFunction<AsyncF> {
    const debounced = debounce(f, waitMilliseconds, options);
    const asyncDebounced = function (...rest: unknown[]) {
        return debounced.apply(this, rest).then((promise: Promise<ReturnType<AsyncF>>) => promise);
    } as DebouncedAsyncFunction<AsyncF>;
    asyncDebounced.cancel = debounced.cancel;
    return asyncDebounced;
}
