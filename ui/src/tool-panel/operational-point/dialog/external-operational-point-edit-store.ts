import {
    OperationalPoint,
    OperationalPointRaideType,
    OperationalPointRinfType,
} from 'track-layout/track-layout-model';
import {
    FieldValidationIssue,
    FieldValidationIssueType,
    isPropEditFieldCommitted,
    PropEdit,
    validate,
} from 'utils/validation-utils';
import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { filterNotEmpty } from 'utils/array-utils';
import { validateRinfCode } from './operational-point-rinf-code-field';
import { OperationalPointSaveRequestBase } from 'tool-panel/operational-point/operational-point-utils';

export type ExternalOperationalPointSaveRequest = OperationalPointSaveRequestBase & {
    rinfType?: OperationalPointRinfType;
};

export type ExternalOperationalPointEditState = {
    existingOperationalPoint?: OperationalPoint;
    isSaving: boolean;
    operationalPoint: ExternalOperationalPointSaveRequest;
    editingRinfCode: boolean;
    validationIssues: FieldValidationIssue<ExternalOperationalPointSaveRequest>[];
    committedFields: (keyof ExternalOperationalPointSaveRequest)[];
    allFieldsCommitted: boolean;
};

export const initialExternalOperationalPointEditState: ExternalOperationalPointEditState = {
    existingOperationalPoint: undefined,
    isSaving: false,
    operationalPoint: {
        rinfType: undefined,
        rinfCodeOverride: undefined,
    },
    editingRinfCode: false,
    validationIssues: [],
    committedFields: [],
    allFieldsCommitted: false,
};

const validateExternalOperationalPoint = (
    saveRequest: ExternalOperationalPointSaveRequest,
    editingRinfCode: boolean,
    raideType?: OperationalPointRaideType,
): FieldValidationIssue<ExternalOperationalPointSaveRequest>[] => {
    const isOlp = raideType === 'OLP';
    const rinfTypeMissing = validate<ExternalOperationalPointSaveRequest>(
        isOlp || saveRequest.rinfType !== undefined,
        {
            field: 'rinfType',
            reason: 'mandatory-field',
            type: FieldValidationIssueType.ERROR,
        },
    );

    const rinfCodeValidation =
        editingRinfCode && saveRequest.rinfCodeOverride
            ? validate<ExternalOperationalPointSaveRequest>(
                  validateRinfCode(saveRequest.rinfCodeOverride) === undefined,
                  {
                      field: 'rinfCodeOverride',
                      reason: 'invalid-rinf-code',
                      type: FieldValidationIssueType.ERROR,
                  },
              )
            : undefined;

    return [rinfTypeMissing, rinfCodeValidation].filter(filterNotEmpty);
};

const internalOperationalPointEditSlice = createSlice({
    name: 'operationalPointEdit',
    initialState: initialExternalOperationalPointEditState,
    reducers: {
        onOperationalPointLoaded: (
            state: ExternalOperationalPointEditState,
            { payload: existingOperationalPoint }: PayloadAction<OperationalPoint>,
        ): void => {
            state.existingOperationalPoint = existingOperationalPoint;
            state.operationalPoint = {
                rinfType: existingOperationalPoint.rinfType,
                rinfCodeOverride: existingOperationalPoint.rinfCodeOverride,
            };
            state.editingRinfCode = !!existingOperationalPoint.rinfCodeOverride;
            state.validationIssues = validateExternalOperationalPoint(
                state.operationalPoint,
                state.editingRinfCode,
                existingOperationalPoint.raideType,
            );
        },
        onUpdateProp: function <TKey extends keyof ExternalOperationalPointSaveRequest>(
            state: ExternalOperationalPointEditState,
            {
                payload: propEdit,
            }: PayloadAction<PropEdit<ExternalOperationalPointSaveRequest, TKey>>,
        ) {
            if (state.operationalPoint) {
                if (propEdit.key === 'rinfType' && propEdit.value === undefined) {
                    delete state.operationalPoint.rinfType;
                } else if (propEdit.key === 'rinfCodeOverride' && propEdit.value === '') {
                    state.operationalPoint.rinfCodeOverride = undefined;
                } else {
                    state.operationalPoint[propEdit.key] = propEdit.value;
                }
                state.validationIssues = validateExternalOperationalPoint(
                    state.operationalPoint,
                    state.editingRinfCode,
                    state.existingOperationalPoint?.raideType,
                );

                if (
                    isPropEditFieldCommitted(
                        propEdit,
                        state.committedFields,
                        state.validationIssues,
                    )
                ) {
                    // Valid value entered for a field, mark that field as committed
                    state.committedFields = [...state.committedFields, propEdit.key];
                }
            }
        },
        onCommitField: function <TKey extends keyof ExternalOperationalPointSaveRequest>(
            state: ExternalOperationalPointEditState,
            { payload: key }: PayloadAction<TKey>,
        ) {
            state.committedFields = [...state.committedFields, key];
        },
        setEditingRinfCode: (
            state: ExternalOperationalPointEditState,
            { payload: editing }: PayloadAction<boolean>,
        ): void => {
            state.editingRinfCode = editing;
            state.validationIssues = validateExternalOperationalPoint(
                state.operationalPoint,
                state.editingRinfCode,
                state.existingOperationalPoint?.raideType,
            );
        },
        validate: (state: ExternalOperationalPointEditState): void => {
            if (state.operationalPoint) {
                state.validationIssues = validateExternalOperationalPoint(
                    state.operationalPoint,
                    state.editingRinfCode,
                    state.existingOperationalPoint?.raideType,
                );
                state.allFieldsCommitted = true;
            }
        },
        onStartSaving: (state: ExternalOperationalPointEditState): void => {
            state.isSaving = true;
        },
        onEndSaving: (state: ExternalOperationalPointEditState): void => {
            state.isSaving = false;
        },
    },
});

export const reducer = internalOperationalPointEditSlice.reducer;
export const actions = internalOperationalPointEditSlice.actions;
