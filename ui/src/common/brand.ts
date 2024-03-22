export type Brand<T, B> = T & { __brand: B };

/**
 * Assert that the given string is in fact the branded kind of thing. This is always a downcast.
 */
export function brand<B>(thing: string): Brand<string, B>;
/**
 * Assert that the given optional string is in fact the branded kind of thing. This is always a downcast.
 */
export function brand<B>(thing: string | undefined): Brand<string, B> | undefined;
/**
 * Assert that the given thing is in fact the branded kind of thing. This is always a downcast.
 */
export function brand<T, B>(thing: T): Brand<T, B>;

export function brand<T, B>(thing: T): Brand<T, B> {
    return thing as unknown as Brand<T, B>;
}
