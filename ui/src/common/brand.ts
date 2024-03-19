export type Brand<T, B> = T & { __brand: B };

export function brand<B>(thing: string): B;
export function brand<T, B>(thing: T): B {
    return thing as unknown as B;
}
