import { OperationalPoint } from 'track-layout/track-layout-model';
import {
    FieldValidationIssue,
    FieldValidationIssueType,
    isPropEditFieldCommitted,
    PropEdit,
    validate,
} from 'utils/validation-utils';
import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { filterNotEmpty } from 'utils/array-utils';

export type ExternalOperationalPointSaveRequest = {
    rinfType?: number;
};

export type ExternalOperationalPointEditState = {
    existingOperationalPoint?: OperationalPoint;
    isSaving: boolean;
    operationalPoint: ExternalOperationalPointSaveRequest;
    validationIssues: FieldValidationIssue<ExternalOperationalPointSaveRequest>[];
    committedFields: (keyof ExternalOperationalPointSaveRequest)[];
    allFieldsCommitted: boolean;
};

export const initialExternalOperationalPointEditState: ExternalOperationalPointEditState = {
    existingOperationalPoint: undefined,
    isSaving: false,
    operationalPoint: {
        rinfType: undefined,
    },
    validationIssues: [],
    committedFields: [],
    allFieldsCommitted: false,
};

const validateExternalOperationalPoint = (
    saveRequest: ExternalOperationalPointSaveRequest,
): FieldValidationIssue<ExternalOperationalPointSaveRequest>[] => {
    const rinfTypeMissing = validate<ExternalOperationalPointSaveRequest>(
        saveRequest.rinfType !== undefined,
        {
            field: 'rinfType',
            reason: 'mandatory-field',
            type: FieldValidationIssueType.ERROR,
        },
    );

    return [rinfTypeMissing].filter(filterNotEmpty);
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
            };
            state.validationIssues = validateExternalOperationalPoint(state.operationalPoint);
        },
        onUpdateProp: function <TKey extends keyof ExternalOperationalPointSaveRequest>(
            state: ExternalOperationalPointEditState,
            {
                payload: propEdit,
            }: PayloadAction<PropEdit<ExternalOperationalPointSaveRequest, TKey>>,
        ) {
            if (state.operationalPoint) {
                state.operationalPoint[propEdit.key] = propEdit.value;
                state.validationIssues = validateExternalOperationalPoint(state.operationalPoint);

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
        validate: (state: ExternalOperationalPointEditState): void => {
            if (state.operationalPoint) {
                state.validationIssues = validateExternalOperationalPoint(state.operationalPoint);
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
