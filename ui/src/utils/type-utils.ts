import { Coordinate } from 'ol/coordinate';

export type Override<T1, T2> = Omit<T1, keyof T2> & T2;

export type Prop<TObject, TKey extends keyof TObject> = {
    key: TKey;
    value: TObject[TKey];
};

export type ValueOf<T> = T[keyof T];

export type GetElementType<T> = T extends (infer TItem)[] ? TItem : never;

type GetStringKeyTypes<TKeys> = TKeys extends string ? TKeys : never;
type EnsureAllKeys<TAllKeys, TGivenKeys> =
    Exclude<TAllKeys, TGivenKeys> extends never
        ? TGivenKeys
        : `Key '${GetStringKeyTypes<Exclude<TAllKeys, TGivenKeys>>}' is missing!`;

export type NonNullableField<T, K extends keyof T> = T & Required<{ [P in K]: NonNullable<T[P]> }>;

/**
 * This function provides type checking to make sure that the given
 * "keys" array contains all possible values for the given key type.
 *
 * Usage (notice that this function returns a function):
 * ensureAllKeys<SomeKeyType>()(['foo', 'xxx', etc])
 *
 * @returns function
 */
export function ensureAllKeys<TKey>() {
    return function <TGivenKey extends TKey>(keys: TGivenKey[]): EnsureAllKeys<TKey, TGivenKey>[] {
        return keys as unknown as EnsureAllKeys<TKey, TGivenKey>[];
    };
}

export const isArray = (value: unknown): value is unknown[] => Array.isArray(value);

/**
 * This variable is used as the return type in a switch statement's
 * default case. Using it increases type safety; the compiler will
 * fail unless all cases are covered in the switch statement.
 *
 * Usage:
 * default:
 *   return exhaustiveMatchingGuard(expression)
 */
export const exhaustiveMatchingGuard = (_: never): never => {
    throw new Error('Should not have reached this code');
};

export const isNil = <T>(object: T | undefined | null): object is undefined | null =>
    object === undefined || object === null;

// Prefer actual nil checks, only use this if you KNOW the index exists
export const expectDefined = <T>(thing: T): NonNullable<T> => {
    if (!isNil(thing)) {
        return thing!;
    } else {
        throw Error('Encountered an unexpected nil!');
    }
};

export const expectFieldDefined = <T, K extends keyof T>(
    thing: T,
    key: K,
): NonNullableField<T, K> => {
    if (!isNil(thing[key])) {
        return thing! as NonNullableField<T, K>;
    } else {
        throw Error('Encountered an unexpected nil!');
    }
};

export const expectCoordinate = (coord: Coordinate): [number, number] => [
    expectDefined(coord[0]),
    expectDefined(coord[1]),
];

export const ifDefined = <T, S>(value: T | undefined, callback: (value: T) => S) =>
    !isNil(value) ? callback(value!) : undefined;

function assertIsDefined<T>(val: T): asserts val is NonNullable<T> {
    if (val === undefined || val === null) {
        throw new Error(`Expected 'val' to be defined, but received ${val}`);
    }
}

export const tuple = <T>(value1: T, value2: T): [NonNullable<T>, NonNullable<T>] => {
    assertIsDefined(value1);
    assertIsDefined(value2);
    return [value1, value2];
};
