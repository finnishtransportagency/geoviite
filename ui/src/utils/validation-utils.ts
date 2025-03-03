import { TOptions } from 'i18next';

export enum FieldValidationIssueType {
    WARNING = 'WARNING',
    ERROR = 'ERROR',
}

export type FieldValidationIssue<TEntity> = {
    field: keyof TEntity;
    reason: string;
    type: FieldValidationIssueType;
    params?: TOptions;
};

export type PropEdit<T, TKey extends keyof T> = {
    key: TKey;
    value: T[TKey];
    editingExistingValue: boolean;
};

// When editing something pre-existing previous values should be committed from
// the start. When creating something new, only mark the field as committed when
// a valid value has been reached in order not to show annoying errors before that.
export function isPropEditFieldCommitted<T, TKey extends keyof T>(
    propEdit: PropEdit<T, TKey>,
    committedFields: TKey[],
    validationIssues: FieldValidationIssue<T>[],
) {
    return (
        propEdit.editingExistingValue ||
        (!committedFields.includes(propEdit.key) &&
            !validationIssues.some((issue) => issue.field === propEdit.key))
    );
}

export const validate = <T>(isValid: boolean, issue: FieldValidationIssue<T>) =>
    isValid ? undefined : issue;

export function getVisibleErrorsByProp<T>(
    committedFields: (keyof T)[],
    validationIssues: FieldValidationIssue<T>[],
    prop: keyof T,
): string[] {
    return committedFields.includes(prop)
        ? validationIssues.filter((error) => error.field === prop).map((error) => error.reason)
        : [];
}

export const hasErrors = <T>(
    committedFields: (keyof T)[],
    validationIssues: FieldValidationIssue<T>[],
    prop: keyof T,
) => getVisibleErrorsByProp(committedFields, validationIssues, prop).length > 0;
