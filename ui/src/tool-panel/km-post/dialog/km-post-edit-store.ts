import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { KmPostSaveRequest } from 'linking/linking-model';
import { LayoutKmPost, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import {
    isPropEditFieldCommitted,
    PropEdit,
    FieldValidationIssue,
    FieldValidationIssueType,
} from 'utils/validation-utils';
import { isNilOrBlank } from 'utils/string-utils';
import { filterNotEmpty } from 'utils/array-utils';

export type KmPostEditState = {
    isNewKmPost: boolean;
    existingKmPost?: LayoutKmPost;
    trackNumberKmPost?: LayoutKmPost;
    kmPost: KmPostSaveRequest;
    isSaving: boolean;
    validationIssues: FieldValidationIssue<KmPostSaveRequest>[];
    committedFields: (keyof KmPostSaveRequest)[];
    allFieldsCommitted: boolean;
};

export const initialKmPostEditState: KmPostEditState = {
    isNewKmPost: false,
    existingKmPost: undefined,
    kmPost: {
        kmNumber: '',
    },
    isSaving: false,
    validationIssues: [],
    committedFields: [],
    allFieldsCommitted: false,
};

function newLinkingKmPost(trackNumberId: LayoutTrackNumberId | undefined): KmPostSaveRequest {
    return {
        kmNumber: '',
        state: undefined,
        trackNumberId: trackNumberId,
    };
}

function validateLinkingKmPost(state: KmPostEditState): FieldValidationIssue<KmPostSaveRequest>[] {
    let errors = [
        ...['kmNumber', 'state', 'trackNumberId']
            .map((prop: keyof KmPostSaveRequest) => {
                if (isNilOrBlank(state.kmPost[prop])) {
                    return {
                        field: prop,
                        reason: 'mandatory-field',
                        type: FieldValidationIssueType.ERROR,
                    };
                }
            })
            .filter(filterNotEmpty),
    ];

    if (state.kmPost.kmNumber.length > 0) {
        if (!isValidKmNumber(state.kmPost.kmNumber)) {
            errors = [...errors, ...getKmNumberDoesntMatchRegExpError()];
        }
    }

    if (state.trackNumberKmPost && state.existingKmPost?.id !== state.trackNumberKmPost?.id) {
        errors = [...errors, ...getErrorForKmPostExistsOnTrack()];
    }

    return errors;
}

function getKmNumberDoesntMatchRegExpError(): FieldValidationIssue<KmPostSaveRequest>[] {
    return [
        {
            field: 'kmNumber',
            reason: 'km-post-regexp',
            type: FieldValidationIssueType.ERROR,
        },
    ];
}

function getErrorForKmPostExistsOnTrack(): FieldValidationIssue<KmPostSaveRequest>[] {
    return [
        ...['kmNumber'].map((prop: keyof KmPostSaveRequest) => {
            return {
                field: prop,
                reason: 'km-number-already-in-use',
                type: FieldValidationIssueType.ERROR,
            };
        }),
    ];
}

const kmPostEditSlice = createSlice({
    name: 'kmPostEdit',
    initialState: initialKmPostEditState,
    reducers: {
        init: (): KmPostEditState => initialKmPostEditState,
        initWithNewKmPost: (
            state: KmPostEditState,
            { payload: trackNumberId }: PayloadAction<LayoutTrackNumberId | undefined>,
        ): void => {
            state.isNewKmPost = true;
            state.kmPost = newLinkingKmPost(trackNumberId);
            state.validationIssues = validateLinkingKmPost(state);
        },
        onKmPostLoaded: (
            state: KmPostEditState,
            { payload: existingKmPost }: PayloadAction<LayoutKmPost>,
        ): void => {
            state.existingKmPost = existingKmPost;
            state.kmPost = existingKmPost;
            state.validationIssues = validateLinkingKmPost(state);
        },
        onUpdateProp: function <TKey extends keyof KmPostSaveRequest>(
            state: KmPostEditState,
            { payload: propEdit }: PayloadAction<PropEdit<KmPostSaveRequest, TKey>>,
        ) {
            if (state.kmPost) {
                state.kmPost[propEdit.key] = propEdit.value;
                state.validationIssues = validateLinkingKmPost(state);

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
        onStartSaving: (state: KmPostEditState): void => {
            state.isSaving = true;
        },
        onEndSaving: (state: KmPostEditState): void => {
            state.isSaving = false;
        },
        onCommitField: function <TKey extends keyof KmPostSaveRequest>(
            state: KmPostEditState,
            { payload: key }: PayloadAction<TKey>,
        ) {
            state.committedFields = [...state.committedFields, key];
        },
        validate: (state: KmPostEditState): void => {
            if (state.kmPost) {
                state.validationIssues = validateLinkingKmPost(state);
                state.allFieldsCommitted = true;
            }
        },
        onTrackNumberKmPostFound: (
            state: KmPostEditState,
            { payload: tnKmPost }: PayloadAction<LayoutKmPost | undefined>,
        ): void => {
            state.trackNumberKmPost = tnKmPost;
            state.validationIssues = validateLinkingKmPost(state);
        },
    },
});

export function canSaveKmPost(state: KmPostEditState): boolean {
    return !!(state.kmPost && !state.validationIssues.length && !state.isSaving);
}

export function isValidKmNumber(kmNumber: string): boolean {
    if (kmNumber.length <= 4) {
        return /^\d{4}$/.test(kmNumber);
    } else if (kmNumber.length <= 6) {
        return /^\d{4}[A-Z]{1,2}$/.test(kmNumber);
    }
    return false;
}

export const reducer = kmPostEditSlice.reducer;
export const actions = kmPostEditSlice.actions;
