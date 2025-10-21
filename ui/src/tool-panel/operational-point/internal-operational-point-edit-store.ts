import { OperationalPoint, OperationalPointState, UICCode } from 'track-layout/track-layout-model';
import {
    FieldValidationIssue,
    FieldValidationIssueType,
    isPropEditFieldCommitted,
    PropEdit,
    validate,
} from 'utils/validation-utils';
import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { isNilOrBlank } from 'utils/string-utils';
import { filterNotEmpty } from 'utils/array-utils';

export const UIC_CODE_REGEX = /^[0-9]+$/g;
export const NAME_REGEX = /^[A-ZÄÖÅa-zäöå0-9 _\-\\!?]+$/g;
const UIC_CODE_MAX_LENGTH = 20;
const ABBREVIATION_MAX_LENGTH = 20;
const NAME_MAX_LENGTH = 150;

export type InternalOperationalPointSaveRequest = {
    name: string;
    abbreviation?: string;
    rinfType?: number;
    state?: OperationalPointState;
    uicCode: UICCode;
};

function validateInternalOperationalPoint(
    saveRequest: InternalOperationalPointSaveRequest,
): FieldValidationIssue<InternalOperationalPointSaveRequest>[] {
    const mandatoryFieldErrors: FieldValidationIssue<InternalOperationalPointSaveRequest>[] = [
        saveRequest.rinfType === undefined
            ? {
                  field: 'rinfType' as keyof InternalOperationalPointSaveRequest,
                  reason: 'mandatory-field',
                  type: FieldValidationIssueType.ERROR,
              }
            : undefined,
        ...['name', 'state', 'uicCode'].map(
            (prop: keyof Omit<InternalOperationalPointSaveRequest, 'rinfType'>) =>
                isNilOrBlank(saveRequest[prop])
                    ? {
                          field: prop,
                          reason: 'mandatory-field',
                          type: FieldValidationIssueType.ERROR,
                      }
                    : undefined,
        ),
    ].filter(filterNotEmpty);
    const regexAndLengthErrors: FieldValidationIssue<InternalOperationalPointSaveRequest>[] = [
        validate<InternalOperationalPointSaveRequest>(
            !saveRequest.uicCode ||
                (!!saveRequest.uicCode.match(UIC_CODE_REGEX) &&
                    saveRequest.uicCode.length <= UIC_CODE_MAX_LENGTH),
            {
                field: 'uicCode',
                reason: 'invalid-uic-code',
                type: FieldValidationIssueType.ERROR,
            },
        ),
        validate<InternalOperationalPointSaveRequest>(
            !saveRequest.abbreviation ||
                (!!saveRequest.abbreviation.match(NAME_REGEX) &&
                    saveRequest.abbreviation.length <= ABBREVIATION_MAX_LENGTH),
            {
                field: 'abbreviation',
                reason: 'invalid-abbreviation',
                type: FieldValidationIssueType.ERROR,
                params: { max: ABBREVIATION_MAX_LENGTH },
            },
        ),
        validate<InternalOperationalPointSaveRequest>(
            !saveRequest.name ||
                (!!saveRequest.name.match(NAME_REGEX) &&
                    saveRequest.name.length <= NAME_MAX_LENGTH),
            {
                field: 'name',
                reason: 'invalid-name',
                type: FieldValidationIssueType.ERROR,
                params: { max: NAME_MAX_LENGTH },
            },
        ),
    ].filter(filterNotEmpty);

    return [...mandatoryFieldErrors, ...regexAndLengthErrors];
}

export type InternalOperationalPointEditState = {
    isNewOperationalPoint: boolean;
    existingOperationalPoint?: OperationalPoint;
    isSaving: boolean;
    operationalPoint: InternalOperationalPointSaveRequest;
    validationIssues: FieldValidationIssue<InternalOperationalPointSaveRequest>[];
    committedFields: (keyof InternalOperationalPointSaveRequest)[];
    allFieldsCommitted: boolean;
};

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
