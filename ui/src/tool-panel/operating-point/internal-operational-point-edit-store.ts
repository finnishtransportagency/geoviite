import { OperationalPoint, OperationalPointState, UICCode } from 'track-layout/track-layout-model';
import {
    FieldValidationIssue,
    FieldValidationIssueType,
    isPropEditFieldCommitted,
    PropEdit,
} from 'utils/validation-utils';
import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { isNilOrBlank } from 'utils/string-utils';
import { filterNotEmpty } from 'utils/array-utils';

export type InternalOperationalPointSaveRequest = {
    name?: string;
    abbreviation?: string;
    rinfType?: number;
    state?: OperationalPointState;
    uicCode?: UICCode;
};

export type InternalOperationalPointEditState = {
    isNewOperationalPoint: boolean;
    existingOperationalPoint?: OperationalPoint;
    isSaving: boolean;
    operationalPoint: InternalOperationalPointSaveRequest;
    validationIssues: FieldValidationIssue<InternalOperationalPointSaveRequest>[];
    committedFields: (keyof InternalOperationalPointSaveRequest)[];
    allFieldsCommitted: boolean;
};

function validateInternalOperationalPoint(
    saveRequest: InternalOperationalPointSaveRequest,
): FieldValidationIssue<InternalOperationalPointSaveRequest>[] {
    const errors: FieldValidationIssue<InternalOperationalPointSaveRequest>[] = [
        saveRequest.rinfType === undefined
            ? {
                  field: 'rinfType' as keyof InternalOperationalPointSaveRequest,
                  reason: 'mandatory-field',
                  type: FieldValidationIssueType.ERROR,
              }
            : undefined,
        ...['name', 'abbreviation', 'state', 'uicCode'].map(
            (prop: keyof Omit<InternalOperationalPointSaveRequest, 'rinfType'>) =>
                isNilOrBlank(saveRequest[prop])
                    ? {
                          field: prop,
                          reason: 'mandatory-field',
                          type: FieldValidationIssueType.ERROR,
                      }
                    : undefined,
        ),
        // TODO: Add more specific validation
    ].filter(filterNotEmpty);

    return errors;
}

export const initialInternalOperationalPointEditState: InternalOperationalPointEditState = {
    isNewOperationalPoint: false,
    existingOperationalPoint: undefined,
    isSaving: false,
    operationalPoint: {
        name: '',
        abbreviation: '',
        rinfType: undefined,
        state: undefined,
        uicCode: '',
    },
    validationIssues: [],
    committedFields: [],
    allFieldsCommitted: false,
};

const internalOperationalPointEditSlice = createSlice({
    name: 'operationalPointEdit',
    initialState: initialInternalOperationalPointEditState,
    reducers: {
        onOperationalPointLoaded: (
            state: InternalOperationalPointEditState,
            { payload: existingOperationalPoint }: PayloadAction<OperationalPoint>,
        ): void => {
            state.existingOperationalPoint = existingOperationalPoint;
            state.operationalPoint = {
                name: existingOperationalPoint.name,
                abbreviation: existingOperationalPoint.abbreviation,
                rinfType: existingOperationalPoint.rinfType,
                state: existingOperationalPoint.state,
                uicCode: existingOperationalPoint.uicCode,
            };
            state.validationIssues = validateInternalOperationalPoint(state.operationalPoint);
        },
        onUpdateProp: function <TKey extends keyof InternalOperationalPointSaveRequest>(
            state: InternalOperationalPointEditState,
            {
                payload: propEdit,
            }: PayloadAction<PropEdit<InternalOperationalPointSaveRequest, TKey>>,
        ) {
            if (state.operationalPoint) {
                state.operationalPoint[propEdit.key] = propEdit.value;
                state.validationIssues = validateInternalOperationalPoint(state.operationalPoint);

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
        onCommitField: function <TKey extends keyof InternalOperationalPointSaveRequest>(
            state: InternalOperationalPointEditState,
            { payload: key }: PayloadAction<TKey>,
        ) {
            state.committedFields = [...state.committedFields, key];
        },
        validate: (state: InternalOperationalPointEditState): void => {
            if (state.operationalPoint) {
                state.validationIssues = validateInternalOperationalPoint(state.operationalPoint);
                state.allFieldsCommitted = true;
            }
        },
        onStartSaving: (state: InternalOperationalPointEditState): void => {
            state.isSaving = true;
        },
        onEndSaving: (state: InternalOperationalPointEditState): void => {
            state.isSaving = false;
        },
    },
});

export const reducer = internalOperationalPointEditSlice.reducer;
export const actions = internalOperationalPointEditSlice.actions;
