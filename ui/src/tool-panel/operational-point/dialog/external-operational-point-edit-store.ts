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
import {
    OperationalPointSaveRequestBase,
    validateRinfIdOverride,
} from 'tool-panel/operational-point/operational-point-utils';

export type ExternalOperationalPointSaveRequest = OperationalPointSaveRequestBase & {
    rinfType?: OperationalPointRinfType;
};

export type ExternalOperationalPointEditState = {
    existingOperationalPoint?: OperationalPoint;
    isSaving: boolean;
    operationalPoint: ExternalOperationalPointSaveRequest;
    editingRinfId: boolean;
    validationIssues: FieldValidationIssue<ExternalOperationalPointSaveRequest>[];
    committedFields: (keyof ExternalOperationalPointSaveRequest)[];
    allFieldsCommitted: boolean;
};

export const initialExternalOperationalPointEditState: ExternalOperationalPointEditState = {
    existingOperationalPoint: undefined,
    isSaving: false,
    operationalPoint: {
        rinfType: undefined,
        rinfIdOverride: undefined,
    },
    editingRinfId: false,
    validationIssues: [],
    committedFields: [],
    allFieldsCommitted: false,
};

const validateExternalOperationalPoint = (
    saveRequest: ExternalOperationalPointSaveRequest,
    editingRinfId: boolean,
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

    const rinfIdValidation = editingRinfId
        ? validateRinfIdOverride(saveRequest.rinfIdOverride)
        : [];

    return [rinfTypeMissing, ...rinfIdValidation].filter(filterNotEmpty);
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
                rinfIdOverride: existingOperationalPoint.rinfIdOverride,
            };
            state.editingRinfId = !!existingOperationalPoint.rinfIdOverride;
            state.validationIssues = validateExternalOperationalPoint(
                state.operationalPoint,
                state.editingRinfId,
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
                } else if (propEdit.key === 'rinfIdOverride' && propEdit.value === '') {
                    state.operationalPoint.rinfIdOverride = undefined;
                } else {
                    state.operationalPoint[propEdit.key] = propEdit.value;
                }
                state.validationIssues = validateExternalOperationalPoint(
                    state.operationalPoint,
                    state.editingRinfId,
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
        setEditingRinfId: (
            state: ExternalOperationalPointEditState,
            { payload: editing }: PayloadAction<boolean>,
        ): void => {
            state.editingRinfId = editing;
            state.validationIssues = validateExternalOperationalPoint(
                state.operationalPoint,
                state.editingRinfId,
                state.existingOperationalPoint?.raideType,
            );
        },
        validate: (state: ExternalOperationalPointEditState): void => {
            if (state.operationalPoint) {
                state.validationIssues = validateExternalOperationalPoint(
                    state.operationalPoint,
                    state.editingRinfId,
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
