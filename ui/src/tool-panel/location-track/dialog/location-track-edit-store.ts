import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import {
    LayoutLocationTrack,
    LayoutTrackNumber,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { LocationTrackSaveRequest } from 'linking/linking-model';
import { isNullOrBlank } from 'utils/string-utils';
import { filterNotEmpty } from 'utils/array-utils';
import {
    isPropEditFieldCommitted,
    PropEdit,
    ValidationError,
    ValidationErrorType,
} from 'utils/validation-utils';

export type LocationTrackEditState = {
    isNewLocationTrack: boolean;
    existingLocationTrack?: LayoutLocationTrack;
    loading: {
        locationTrack: boolean;
        trackNumbers: boolean;
    };
    isSaving: boolean;
    trackNumbers: LayoutTrackNumber[];
    locationTrack: LocationTrackSaveRequest;
    validationErrors: ValidationError<LocationTrackSaveRequest>[];
    committedFields: (keyof LocationTrackSaveRequest)[];
    allFieldsCommitted: boolean;
};

export const initialLocationTrackEditState: LocationTrackEditState = {
    isNewLocationTrack: false,
    existingLocationTrack: undefined,
    loading: {
        locationTrack: false,
        trackNumbers: false,
    },
    isSaving: false,
    trackNumbers: [],
    locationTrack: {
        name: '',
        state: undefined,
        type: undefined,
        description: '',
        duplicateOf: null,
    },
    validationErrors: [],
    committedFields: [],
    allFieldsCommitted: false,
};

export type LoadingProp = keyof LocationTrackEditState['loading'];

const ALIGNMENT_NAME_REGEX = /^[A-Za-zÄÖÅäöå0-9 \-_]+$/g;

function newLinkingLocationTrack(): LocationTrackSaveRequest {
    return {
        name: '',
        description: '',
        type: undefined,
        state: undefined,
        trackNumberId: undefined,
        duplicateOf: null,
        topologicalConnectivity: undefined,
    };
}

function validateLinkingLocationTrack(
    saveRequest: LocationTrackSaveRequest,
): ValidationError<LocationTrackSaveRequest>[] {
    let errors: ValidationError<LocationTrackSaveRequest>[] = [];

    errors = [
        ...errors,
        ...['name', 'trackNumberId', 'type', 'state', 'description', 'topologicalConnectivity']
            .map((prop: keyof LocationTrackSaveRequest) => {
                if (isNullOrBlank(saveRequest[prop])) {
                    return {
                        field: prop,
                        reason: 'mandatory-field',
                        type: ValidationErrorType.ERROR,
                    };
                }
            })
            .filter(filterNotEmpty),
        ...['name']
            .map((prop: keyof LocationTrackSaveRequest) => {
                const value = saveRequest[prop];
                return value && !value.match(ALIGNMENT_NAME_REGEX)
                    ? {
                          field: prop,
                          reason: `invalid-name`,
                          type: ValidationErrorType.ERROR,
                      }
                    : null;
            })
            .filter(filterNotEmpty),
    ];
    if (
        saveRequest.description &&
        (saveRequest.description.length < 4 || saveRequest.description.length > 256)
    ) {
        return [...errors, ...getErrorForInvalidDescription()];
    }

    return errors;
}

function getErrorForInvalidDescription(): ValidationError<LocationTrackSaveRequest>[] {
    return [
        {
            field: 'description',
            reason: 'invalid-description',
            type: ValidationErrorType.ERROR,
        },
    ];
}

const locationTrackEditSlice = createSlice({
    name: 'locationTrackEdit',
    initialState: initialLocationTrackEditState,
    reducers: {
        initWithNewLocationTrack: (state: LocationTrackEditState): void => {
            state.isNewLocationTrack = true;
            state.locationTrack = newLinkingLocationTrack();
            state.validationErrors = validateLinkingLocationTrack(state.locationTrack);
        },
        onStartLoadingTrackNumbers: (state: LocationTrackEditState) => {
            state.loading.trackNumbers = true;
        },
        onTrackNumbersLoaded: (
            state: LocationTrackEditState,
            { payload: trackNumbers }: PayloadAction<LayoutTrackNumber[]>,
        ): void => {
            state.loading.trackNumbers = false;
            state.trackNumbers = trackNumbers;
        },
        onStartLoadingLocationTrack: (state: LocationTrackEditState) => {
            state.isNewLocationTrack = false;
            state.loading.locationTrack = true;
        },
        onLocationTrackLoaded: (
            state: LocationTrackEditState,
            { payload: existingLocationTrack }: PayloadAction<LayoutLocationTrack>,
        ): void => {
            state.existingLocationTrack = existingLocationTrack;
            state.locationTrack = {
                ...existingLocationTrack,
                type: existingLocationTrack.type || 'MAIN',
                duplicateOf: existingLocationTrack.duplicateOf,
            };
            state.validationErrors = validateLinkingLocationTrack(state.locationTrack);
            state.loading.locationTrack = false;
        },
        onUpdateProp: function <TKey extends keyof LocationTrackSaveRequest>(
            state: LocationTrackEditState,
            { payload: propEdit }: PayloadAction<PropEdit<LocationTrackSaveRequest, TKey>>,
        ) {
            if (state.locationTrack) {
                state.locationTrack[propEdit.key] = propEdit.value;
                state.validationErrors = validateLinkingLocationTrack(state.locationTrack);

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
        onCommitField: function <TKey extends keyof LocationTrackSaveRequest>(
            state: LocationTrackEditState,
            { payload: key }: PayloadAction<TKey>,
        ) {
            state.committedFields = [...state.committedFields, key];
        },
        validate: (state: LocationTrackEditState): void => {
            if (state.locationTrack) {
                state.validationErrors = validateLinkingLocationTrack(state.locationTrack);
                state.allFieldsCommitted = true;
            }
        },
        onStartSaving: (state: LocationTrackEditState): void => {
            state.isSaving = true;
        },
        onSaveSucceed: (
            state: LocationTrackEditState,
            { payload: _payload }: PayloadAction<LocationTrackId>,
        ): void => {
            state.isSaving = false;
        },
        onSaveFailed: (state: LocationTrackEditState): void => {
            state.isSaving = false;
        },
    },
});

export function isLoading(state: LocationTrackEditState): boolean {
    return Object.keys(state.loading).some((key: LoadingProp) => state.loading[key]);
}

export function isProcessing(state: LocationTrackEditState): boolean {
    return isLoading(state) || state.isSaving;
}

export function canSaveLocationTrack(state: LocationTrackEditState): boolean {
    return !!(state.locationTrack && !state.validationErrors.length && !isProcessing(state));
}

export const reducer = locationTrackEditSlice.reducer;
export const actions = locationTrackEditSlice.actions;
