import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { KmPostSaveRequest } from 'linking/linking-model';
import {
    LayoutKmPost,
    LayoutKmPostId,
    LayoutTrackNumber,
    LayoutTrackNumberId,
} from 'track-layout/track-layout-model';
import { GeometryTrackNumberId } from 'geometry/geometry-model';
import {
    isPropEditFieldCommitted,
    PropEdit,
    ValidationError,
    ValidationErrorType,
} from 'utils/validation-utils';
import { isNilOrBlank } from 'utils/string-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { KmNumber } from 'common/common-model';

export type KmPostEditState = {
    isNewKmPost: boolean;
    existingKmPost?: LayoutKmPost;
    kmPost: KmPostSaveRequest;
    loading: {
        trackNumbers: boolean;
        kmPost: boolean;
    };
    isSaving: boolean;
    trackNumbers: LayoutTrackNumber[];
    validationErrors: ValidationError<KmPostSaveRequest>[];
    committedFields: (keyof KmPostSaveRequest)[];
    allFieldsCommitted: boolean;
    baselineKmNumber: KmNumber | undefined;
    baselineTrackNumberId: LayoutTrackNumberId | undefined;
};

export const initialKmPostEditState: KmPostEditState = {
    isNewKmPost: false,
    existingKmPost: undefined,
    kmPost: {
        kmNumber: '',
    },
    loading: {
        trackNumbers: false,
        kmPost: false,
    },
    isSaving: false,
    trackNumbers: [],
    validationErrors: [],
    committedFields: [],
    allFieldsCommitted: false,
    baselineKmNumber: undefined,
    baselineTrackNumberId: undefined,
};

function newLinkingKmPost(trackNumberId: GeometryTrackNumberId | undefined): KmPostSaveRequest {
    return {
        kmNumber: '',
        state: undefined,
        trackNumberId: trackNumberId,
    };
}

function validateLinkingKmPost(kmPost: KmPostSaveRequest): ValidationError<KmPostSaveRequest>[] {
    const errors = [
        ...['kmNumber', 'state', 'trackNumberId']
            .map((prop: keyof KmPostSaveRequest) => {
                if (isNilOrBlank(kmPost[prop])) {
                    return {
                        field: prop,
                        reason: 'error-mandatory-field',
                        type: ValidationErrorType.ERROR,
                    };
                }
            })
            .filter(filterNotEmpty),
    ];

    if (kmPost.kmNumber.length > 0) {
        if (!isValidKmNumber(kmPost.kmNumber)) {
            return [...errors, ...getKmNumberDoesntMatchRegExpError()];
        }
    }

    return errors;
}

function getKmNumberDoesntMatchRegExpError(): ValidationError<KmPostSaveRequest>[] {
    return [
        {
            field: 'kmNumber',
            reason: 'error-regexp',
            type: ValidationErrorType.ERROR,
        },
    ];
}

function getErrorForKmPostExistsOnTrack(): ValidationError<KmPostSaveRequest>[] {
    return [
        ...['kmNumber'].map((prop: keyof KmPostSaveRequest) => {
            return {
                field: prop,
                reason: 'error-km-number-already-in-use',
                type: ValidationErrorType.ERROR,
            };
        }),
    ];
}

const kmPostEditSlice = createSlice({
    name: 'kmPostEdit',
    initialState: initialKmPostEditState,
    reducers: {
        initWithNewKmPost: (
            state: KmPostEditState,
            { payload: trackNumberId }: PayloadAction<GeometryTrackNumberId | undefined>,
        ): void => {
            state.isNewKmPost = true;
            state.kmPost = newLinkingKmPost(trackNumberId);
            state.validationErrors = validateLinkingKmPost(state.kmPost);
        },
        onUpdateProp: function <TKey extends keyof KmPostSaveRequest>(
            state: KmPostEditState,
            { payload: propEdit }: PayloadAction<PropEdit<KmPostSaveRequest, TKey>>,
        ) {
            if (state.kmPost) {
                state.kmPost[propEdit.key] = propEdit.value;
                state.validationErrors = validateLinkingKmPost(state.kmPost);

                if (
                    isPropEditFieldCommitted(
                        propEdit,
                        state.committedFields,
                        state.validationErrors,
                    )
                ) {
                    // Valid value entered for a field, mark that field as committed
                    state.committedFields = [...state.committedFields, propEdit.key];
                }
            }
        },
        onStartLoadingTrackNumbers: (state: KmPostEditState) => {
            state.loading.trackNumbers = true;
        },
        onTrackNumbersLoaded: (
            state: KmPostEditState,
            { payload: trackNumbers }: PayloadAction<LayoutTrackNumber[]>,
        ): void => {
            state.loading.trackNumbers = false;
            state.trackNumbers = trackNumbers;
        },
        onStartLoadingKmPost: (state: KmPostEditState) => {
            state.isNewKmPost = false;
            state.loading.kmPost = true;
        },
        onKmPostLoaded: (
            state: KmPostEditState,
            { payload: existingKmPost }: PayloadAction<LayoutKmPost>,
        ): void => {
            state.existingKmPost = existingKmPost;
            state.kmPost = existingKmPost;

            state.baselineKmNumber = existingKmPost.kmNumber;
            state.baselineTrackNumberId = existingKmPost.trackNumberId;

            state.validationErrors = validateLinkingKmPost(state.kmPost);
            state.loading.kmPost = false;
        },
        onStartSaving: (state: KmPostEditState): void => {
            state.isSaving = true;
        },
        onSaveSucceed: (
            state: KmPostEditState,
            { payload: _kmPostId }: PayloadAction<LayoutKmPostId>,
        ): void => {
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
                state.validationErrors = validateLinkingKmPost(state.kmPost);
                state.allFieldsCommitted = true;
            }
        },
        onSaveFailed: (state: KmPostEditState): void => {
            state.isSaving = false;
        },
        onKmNumberExistsOnTrack: (state: KmPostEditState): void => {
            state.validationErrors = [
                ...state.validationErrors,
                ...getErrorForKmPostExistsOnTrack(),
            ];
        },
    },
});

export function canSaveKmPost(state: KmPostEditState): boolean {
    return !!(state.kmPost && !state.validationErrors.length && !state.isSaving);
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
