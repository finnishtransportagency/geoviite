export function objectEquals(o1: unknown, o2: unknown): boolean {
    return JSON.stringify(o1) === JSON.stringify(o2);
}

export const prevIfObjectsEqual = <T>(prev: T, next: T): T =>
    objectEquals(prev, next) ? prev : next;

export function mapOptional<T, S>(o: T | undefined, mapper: (o: T) => S): S | undefined {
    return o !== undefined ? mapper(o) : undefined;
}

export function mapOptionalAsync<T, S>(
    o: T | undefined,
    mapper: (o: T) => Promise<S | undefined>,
): Promise<S | undefined> {
    return o !== undefined ? mapper(o) : Promise.resolve(undefined);
}
