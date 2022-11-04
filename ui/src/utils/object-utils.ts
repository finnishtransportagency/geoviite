export function objectEquals(o1: unknown, o2: unknown): boolean {
    return JSON.stringify(o1) === JSON.stringify(o2);
}

export function mapNull<T, S>(o: T | null, mapper: (o: T) => S): S | null {
    return o != null ? mapper(o) : null;
}

export function mapNullAsync<T, S>(
    o: T | null,
    mapper: (o: T) => Promise<S | null>,
): Promise<S | null> {
    return o != null ? mapper(o) : Promise.resolve(null);
}
