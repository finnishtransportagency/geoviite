import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import {
    LayoutLocationTrack,
    LayoutTrackNumber,
    LocationTrackNaming,
    LocationTrackNamingScheme,
} from 'track-layout/track-layout-model';
import { LocationTrackSaveRequest } from 'linking/linking-model';
import { isNilOrBlank } from 'utils/string-utils';
import { filterNotEmpty } from 'utils/array-utils';
import {
    FieldValidationIssue,
    FieldValidationIssueType,
    isPropEditFieldCommitted,
    PropEdit,
} from 'utils/validation-utils';
import { LocationTrackOwner, LocationTrackOwnerId } from 'common/common-model';
import {
    validateLocationTrackDescriptionBase,
    validateLocationTrackName,
} from 'tool-panel/location-track/dialog/location-track-validation';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

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
    validationIssues: FieldValidationIssue<LocationTrackSaveRequest>[];
    committedFields: (keyof LocationTrackSaveRequest)[];
    allFieldsCommitted: boolean;
};

export const initialLocationTrackNameUndefined: LocationTrackNaming = {
    scheme: LocationTrackNamingScheme.UNDEFINED,
    freeText: '',
    specifier: undefined,
};

export const initialLocationTrackNameTrackNumber: LocationTrackNaming = {
    scheme: LocationTrackNamingScheme.TRACK_NUMBER_TRACK,
    freeText: '',
    specifier: undefined,
};

export const initialLocationTrackNameBetweenOperatingPoints: LocationTrackNaming = {
    scheme: LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS,
    freeText: undefined,
    specifier: undefined,
};

export const initialLocationTrackNameWithinOperatingPoint: LocationTrackNaming = {
    scheme: LocationTrackNamingScheme.WITHIN_OPERATING_POINT,
    freeText: '',
    specifier: undefined,
};

export const initialLocationTrackNameChord: LocationTrackNaming = {
    scheme: LocationTrackNamingScheme.CHORD,
    freeText: undefined,
    specifier: undefined,
};

export const locationTrackNameByNamingScheme = (namingScheme: LocationTrackNamingScheme) => {
    switch (namingScheme) {
        case LocationTrackNamingScheme.UNDEFINED:
            return initialLocationTrackNameUndefined;
        case LocationTrackNamingScheme.TRACK_NUMBER_TRACK:
            return initialLocationTrackNameTrackNumber;
        case LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS:
            return initialLocationTrackNameBetweenOperatingPoints;
        case LocationTrackNamingScheme.WITHIN_OPERATING_POINT:
            return initialLocationTrackNameWithinOperatingPoint;
        case LocationTrackNamingScheme.CHORD:
            return initialLocationTrackNameChord;
        default:
            return exhaustiveMatchingGuard(namingScheme);
    }
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
        namingScheme: initialLocationTrackNameUndefined,
        trackNumberId: undefined,
        state: undefined,
        type: undefined,
        descriptionBase: '',
        duplicateOf: undefined,
        ownerId: '',
    },
    validationIssues: [],
    committedFields: [],
    allFieldsCommitted: false,
};

export type LoadingProp = keyof LocationTrackEditState['loading'];

function newLinkingLocationTrack(): LocationTrackSaveRequest {
    return {
        namingScheme: initialLocationTrackNameUndefined,
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
): FieldValidationIssue<LocationTrackSaveRequest>[] {
    const errors: FieldValidationIssue<LocationTrackSaveRequest>[] = [
        ...[
            'trackNumberId',
            'type',
            'state',
            'descriptionBase',
            'descriptionSuffix',
            'topologicalConnectivity',
            'ownerId',
        ]
            .map((prop: keyof Omit<LocationTrackSaveRequest, 'namingScheme'>) =>
                isNilOrBlank(saveRequest[prop])
                    ? {
                          field: prop,
                          reason: 'mandatory-field',
                          type: FieldValidationIssueType.ERROR,
                      }
                    : undefined,
            )
            .filter(filterNotEmpty),
        ...validateLocationTrackName(saveRequest.namingScheme),
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
            state.validationIssues = validateLinkingLocationTrack(state.locationTrack);
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
                namingScheme: initialLocationTrackNameUndefined,
                type: existingLocationTrack.type || 'MAIN',
                duplicateOf: existingLocationTrack.duplicateOf,
            };
            state.validationIssues = validateLinkingLocationTrack(state.locationTrack);
            state.loading.locationTrack = false;
        },
        onUpdateProp: function <TKey extends keyof LocationTrackSaveRequest>(
            state: LocationTrackEditState,
            { payload: propEdit }: PayloadAction<PropEdit<LocationTrackSaveRequest, TKey>>,
        ) {
            if (state.locationTrack) {
                state.locationTrack[propEdit.key] = propEdit.value;
                state.validationIssues = validateLinkingLocationTrack(state.locationTrack);

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
        onCommitField: function <TKey extends keyof LocationTrackSaveRequest>(
            state: LocationTrackEditState,
            { payload: key }: PayloadAction<TKey>,
        ) {
            state.committedFields = [...state.committedFields, key];
        },
        validate: (state: LocationTrackEditState): void => {
            if (state.locationTrack) {
                state.validationIssues = validateLinkingLocationTrack(state.locationTrack);
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
    return state.locationTrack && !state.validationIssues.length && !isProcessing(state);
}

export const reducer = locationTrackEditSlice.reducer;
export const actions = locationTrackEditSlice.actions;
