import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { LayoutLocationTrack, LayoutTrackNumber } from 'track-layout/track-layout-model';
import { LocationTrackSaveRequest } from 'linking/linking-model';
import { isNilOrBlank } from 'utils/string-utils';
import { filterNotEmpty } from 'utils/array-utils';
import {
    isPropEditFieldCommitted,
    PropEdit,
    ValidationError,
    ValidationErrorType,
} from 'utils/validation-utils';
import { LocationTrackOwner, LocationTrackOwnerId } from 'common/common-model';
import {
    validateLocationTrackDescriptionBase,
    validateLocationTrackName,
} from 'tool-panel/location-track/dialog/location-track-validation';

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
        trackNumberId: undefined,
        state: undefined,
        type: undefined,
        descriptionBase: '',
        duplicateOf: undefined,
        ownerId: '',
    },
    validationErrors: [],
    committedFields: [],
    allFieldsCommitted: false,
};

export type LoadingProp = keyof LocationTrackEditState['loading'];

function newLinkingLocationTrack(): LocationTrackSaveRequest {
    return {
        name: '',
        descriptionBase: '',
        type: undefined,
        state: undefined,
        trackNumberId: undefined,
        topologicalConnectivity: undefined,
        duplicateOf: undefined,
        ownerId: undefined,
    };
}

function validateLinkingLocationTrack(
    saveRequest: LocationTrackSaveRequest,
): ValidationError<LocationTrackSaveRequest>[] {
    const errors: ValidationError<LocationTrackSaveRequest>[] = [
        ...[
            'trackNumberId',
            'type',
            'state',
            'descriptionBase',
            'descriptionSuffix',
            'topologicalConnectivity',
            'ownerId',
        ]
            .map((prop: keyof LocationTrackSaveRequest) => {
                if (isNilOrBlank(saveRequest[prop])) {
                    return {
                        field: prop,
                        reason: 'mandatory-field',
                        type: ValidationErrorType.ERROR,
                    };
                }
            })
            .filter(filterNotEmpty),
        ...validateLocationTrackName(saveRequest.name),
    ];

    return [...errors, ...validateLocationTrackDescriptionBase(saveRequest.descriptionBase)];
}

const VAYLAVIRASTO_LOCATION_TRACK_OWNER_NAME = 'Väylävirasto';
export function setVaylavirastoOwnerIdFrom(
    owners: LocationTrackOwner[] | undefined,
    set: (vaylaId: LocationTrackOwnerId) => void,
) {
    if (owners !== undefined) {
        const vayla = owners.find((owner) => owner.name === VAYLAVIRASTO_LOCATION_TRACK_OWNER_NAME);
        if (vayla !== undefined) {
            set(vayla.id);
        }
    }
}

const locationTrackEditSlice = createSlice({
    name: 'locationTrackEdit',
    initialState: initialLocationTrackEditState,
    reducers: {
        initWithNewLocationTrack: (
            state: LocationTrackEditState,
            { payload: owners }: PayloadAction<LocationTrackOwner[] | undefined>,
        ): void => {
            state.isNewLocationTrack = true;
            state.locationTrack = newLinkingLocationTrack();
            state.validationErrors = validateLinkingLocationTrack(state.locationTrack);
            setVaylavirastoOwnerIdFrom(owners, (id) => (state.locationTrack.ownerId = id));
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
        onEndSaving: (state: LocationTrackEditState): void => {
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
