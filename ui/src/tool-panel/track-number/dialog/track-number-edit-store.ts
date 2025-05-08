import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import {
    LayoutReferenceLine,
    LayoutState,
    LayoutTrackNumber,
} from 'track-layout/track-layout-model';
import { isEmpty, isEqualWithoutWhitespace, isNilOrBlank } from 'utils/string-utils';
import { filterNotEmpty } from 'utils/array-utils';
import {
    isPropEditFieldCommitted,
    PropEdit,
    FieldValidationIssue,
    FieldValidationIssueType,
} from 'utils/validation-utils';
import { ZERO_TRACK_METER } from 'common/common-model';
import { formatTrackMeter } from 'utils/geography-utils';
import { ALIGNMENT_DESCRIPTION_REGEX } from 'tool-panel/location-track/dialog/location-track-validation';

const TRACK_NUMBER_REGEX = /^[äÄöÖåÅA-Za-z0-9 ]{2,20}$/g;
export const ADDRESS_REGEX = /^\d{1,4}[A-Z]{0,2}(\+\d{1,4}(\.\d{1,3})?)?$/g;
type RegexValidation = {
    field: keyof TrackNumberSaveRequest;
    regex: RegExp;
};
const REGEX_VALIDATIONS: RegexValidation[] = [
    { field: 'number', regex: TRACK_NUMBER_REGEX },
    { field: 'description', regex: ALIGNMENT_DESCRIPTION_REGEX },
    { field: 'startAddress', regex: ADDRESS_REGEX },
];

export type TrackNumberSaveRequest = {
    number: string;
    description: string;
    state: LayoutState;
    startAddress: string;
};

export type TrackNumberEditState = {
    inEditTrackNumber: LayoutTrackNumber | undefined;
    inEditReferenceLine: LayoutReferenceLine | undefined;
    existingTrackNumbers: LayoutTrackNumber[];
    request: TrackNumberSaveRequest;
    validationIssues: FieldValidationIssue<TrackNumberSaveRequest>[];
    committedFields: (keyof TrackNumberSaveRequest)[];
};

export function initialTrackNumberEditState(
    existingTrackNumber: LayoutTrackNumber | undefined,
    existingReferenceLine: LayoutReferenceLine | undefined,
    trackNumbers: LayoutTrackNumber[],
): TrackNumberEditState {
    const state = {
        inEditTrackNumber: existingTrackNumber,
        inEditReferenceLine: existingReferenceLine,
        existingTrackNumbers: trackNumbers,
        request: {
            number: existingTrackNumber?.number || '',
            description: existingTrackNumber?.description || '',
            state: existingTrackNumber?.state || 'IN_USE',
            startAddress: formatTrackMeter(existingReferenceLine?.startAddress || ZERO_TRACK_METER),
        },
        validationIssues: [],
        committedFields: [],
    };
    return {
        ...state,
        validationIssues: validateTrackNumberEdit(state),
    };
}

function validateTrackNumberEdit(
    state: TrackNumberEditState,
): FieldValidationIssue<TrackNumberSaveRequest>[] {
    const mandatoryFieldErrors = ['number', 'description', 'state', 'startAddress'].map(
        (prop: keyof TrackNumberSaveRequest) =>
            isNilOrBlank(state.request[prop])
                ? {
                      field: prop,
                      reason: `mandatory-field-${prop}`,
                      type: FieldValidationIssueType.ERROR,
                  }
                : undefined,
    );
    const regexErrors = REGEX_VALIDATIONS.map((validation) => {
        const value = state.request[validation.field];
        return !isEmpty(value) && !state.request[validation.field].match(validation.regex)
            ? {
                  field: validation.field,
                  reason: `invalid-${validation.field}`,
                  type: FieldValidationIssueType.ERROR,
              }
            : undefined;
    });
    const existingTrackNumber = state.existingTrackNumbers.find((tn) =>
        isEqualWithoutWhitespace(tn.number, state.request.number),
    );
    const otherErrors = [
        existingTrackNumber && existingTrackNumber.id !== state.inEditTrackNumber?.id
            ? {
                  field: 'number' as keyof TrackNumberSaveRequest,
                  reason: 'duplicate-number',
                  type: FieldValidationIssueType.ERROR,
              }
            : undefined,
    ];

    return [...mandatoryFieldErrors, ...regexErrors, ...otherErrors].filter(filterNotEmpty);
}

const trackNumberEditSlice = createSlice({
    name: 'trackNumberEdit',
    initialState: initialTrackNumberEditState(undefined, undefined, []),
    reducers: {
        onUpdateProp: function <TKey extends keyof TrackNumberSaveRequest>(
            state: TrackNumberEditState,
            { payload: propEdit }: PayloadAction<PropEdit<TrackNumberSaveRequest, TKey>>,
        ) {
            state.request[propEdit.key] = propEdit.value;
            state.validationIssues = validateTrackNumberEdit(state);

            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationIssues)) {
                // Valid value entered for a field, mark that field as committed
                state.committedFields = [...state.committedFields, propEdit.key];
            }
        },
        onCommitField: function <TKey extends keyof TrackNumberSaveRequest>(
            state: TrackNumberEditState,
            { payload: key }: PayloadAction<TKey>,
        ) {
            state.committedFields = [...state.committedFields, key];
        },
        onCommitAllFields: (state: TrackNumberEditState): void => {
            state.committedFields = Object.keys(state.request).map(
                (k) => k as keyof TrackNumberSaveRequest,
            );
        },
    },
});

export function getErrors(
    state: TrackNumberEditState,
    key: keyof TrackNumberSaveRequest,
): FieldValidationIssue<TrackNumberSaveRequest>[] {
    return state.committedFields.includes(key)
        ? state.validationIssues.filter((e) => e.field === key)
        : [];
}

export const reducer = trackNumberEditSlice.reducer;
export const actions = trackNumberEditSlice.actions;
