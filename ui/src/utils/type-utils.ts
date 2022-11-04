export type Override<T1, T2> = Omit<T1, keyof T2> & T2;

export type Prop<TObject, TKey extends keyof TObject> = {
    key: TKey;
    value: TObject[TKey];
};

export type ValueOf<T> = T[keyof T];

export type GetElementType<T> = T extends (infer TItem)[] ? TItem : never;

type GetStringKeyTypes<TKeys> = TKeys extends string ? TKeys : never;
type EnsureAllKeys<TAllKeys, TGivenKeys> = Exclude<TAllKeys, TGivenKeys> extends never
    ? TGivenKeys
    : `Key '${GetStringKeyTypes<Exclude<TAllKeys, TGivenKeys>>}' is missing!`;

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
