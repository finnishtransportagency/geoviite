import { filterNotEmpty } from 'utils/array-utils';

export enum ValidationErrorType {
    WARNING = 'WARNING',
    ERROR = 'ERROR',
}

export type ValidationError<TEntity> = {
    field: keyof TEntity;
    reason: string;
    type: ValidationErrorType;
};

export type PropEdit<T, TKey extends keyof T> = {
    key: TKey;
    value: T[TKey];
    editingExistingValue: boolean;
};

const OID_REGEX = /^\d+(\.\d+){2,9}$/g;

// When editing something pre-existing previous values should be committed from
// the start. When creating something new, only mark the field as committed when
// a valid value has been reached in order not to show annoying errors before that.
export function isPropEditFieldCommitted<T, TKey extends keyof T>(
    propEdit: PropEdit<T, TKey>,
    committedFields: TKey[],
    validationErrors: ValidationError<T>[],
) {
    return (
        propEdit.editingExistingValue ||
        (!committedFields.includes(propEdit.key) &&
            !validationErrors.some((error) => error.field == propEdit.key))
    );
}

export function validateOid(oid: string): string[] {
    const regexpMatch = oid.match(OID_REGEX);

    const tooShort = oid.length <= 4;
    const tooLong = oid.length > 50;

    return [
        regexpMatch ? null : 'invalid-oid',
        tooShort ? 'not-enough-values' : null,
        tooLong ? 'too-many-values' : null,
    ].filter(filterNotEmpty);
}

export const validate = <T>(isValid: boolean, error: ValidationError<T>) =>
    isValid ? undefined : error;
