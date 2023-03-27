import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import {
    isPropEditFieldCommitted,
    PropEdit,
    validate,
    ValidationError,
    ValidationErrorType,
} from 'utils/validation-utils';
import { filterNotEmpty } from 'utils/array-utils';
import {
    ElementItem,
    GeometryPlanHeader,
    GeometryType,
    PlanSource,
    VerticalGeometryItem,
} from 'geometry/geometry-model';
import { compareTrackMeterStrings, trackMeterIsValid } from 'common/common-model';
import {
    LayoutKmPost,
    LayoutKmPostLengthDetails,
    LayoutLocationTrack,
    LayoutTrackNumber,
} from 'track-layout/track-layout-model';
import { wrapReducers } from 'store/store-utils';

type SearchGeometries = {
    searchLines: boolean;
    searchCurves: boolean;
    searchClothoids: boolean;
    searchMissingGeometry: boolean;
};

export type PlanGeometrySearchState = {
    source: PlanSource;
    plan: GeometryPlanHeader | undefined;
    searchGeometries: SearchGeometries;

    elements: ElementItem[];
    validationErrors: ValidationError<PlanGeometrySearchState>[];
    committedFields: (keyof PlanGeometrySearchState)[];
};

export type LocationTrackVerticalGeometrySearchParameters = {
    locationTrack: LayoutLocationTrack | undefined;
    startTrackMeter: string;
    endTrackMeter: string;
};

export type LocationTrackVerticalGeometrySearchState = {
    searchFields: LocationTrackVerticalGeometrySearchParameters;
    searchParameters: LocationTrackVerticalGeometrySearchParameters;

    validationErrors: ValidationError<LocationTrackVerticalGeometrySearchParameters>[];
    committedFields: (keyof LocationTrackVerticalGeometrySearchParameters)[];
    verticalGeometry: VerticalGeometryItem[];
};

export type PlanVerticalGeometrySearchState = {
    source: PlanSource;
    plan: GeometryPlanHeader | undefined;

    validationErrors: ValidationError<PlanVerticalGeometrySearchState>[];
    committedFields: (keyof PlanVerticalGeometrySearchState)[];
    verticalGeometry: VerticalGeometryItem[];
};

export type KmLengthsSearchState = {
    trackNumber: LayoutTrackNumber | undefined;
    startKm: LayoutKmPost | undefined;
    endKm: LayoutKmPost | undefined;

    validationErrors: ValidationError<KmLengthsSearchState>[];
    committedFields: (keyof KmLengthsSearchState)[];
    kmLengths: LayoutKmPostLengthDetails[];
};

enum MissingSection {
    MISSING_SECTION = 'MISSING_SECTION',
}

export type GeometryTypeIncludingMissing = GeometryType | MissingSection;

export const initialPlanGeometrySearchState: PlanGeometrySearchState = {
    source: 'GEOMETRIAPALVELU',
    plan: undefined,
    searchGeometries: {
        searchLines: true,
        searchCurves: true,
        searchClothoids: true,
        searchMissingGeometry: false,
    },

    elements: [],
    validationErrors: [],
    committedFields: [],
};

export type ContinuousSearchParameters = {
    locationTrack: LayoutLocationTrack | undefined;
    startTrackMeter: string;
    endTrackMeter: string;
    searchGeometries: SearchGeometries;
};

export type ElementListContinuousGeometrySearchState = {
    searchFields: ContinuousSearchParameters;
    searchParameters: ContinuousSearchParameters;

    elements: ElementItem[];
    validationErrors: ValidationError<ContinuousSearchParameters>[];
    committedFields: (keyof ContinuousSearchParameters)[];
};

export const initialContinuousSearchState: ElementListContinuousGeometrySearchState = {
    searchFields: {
        locationTrack: undefined,
        startTrackMeter: '',
        endTrackMeter: '',
        searchGeometries: {
            searchLines: true,
            searchCurves: true,
            searchClothoids: true,
            searchMissingGeometry: true,
        },
    },

    searchParameters: {
        locationTrack: undefined,
        startTrackMeter: '',
        endTrackMeter: '',
        searchGeometries: {
            searchLines: true,
            searchMissingGeometry: true,
            searchClothoids: true,
            searchCurves: true,
        },
    },

    elements: [],
    validationErrors: [],
    committedFields: [],
};

const initialLocationTrackVerticalGeometrySearchState: LocationTrackVerticalGeometrySearchState = {
    searchFields: {
        locationTrack: undefined,
        startTrackMeter: '',
        endTrackMeter: '',
    },
    searchParameters: {
        locationTrack: undefined,
        startTrackMeter: '',
        endTrackMeter: '',
    },
    validationErrors: [],
    committedFields: [],
    verticalGeometry: [],
};

const initialPlanVerticalGeometrySearchState: PlanVerticalGeometrySearchState = {
    plan: undefined,
    source: 'GEOMETRIAPALVELU',
    validationErrors: [],
    committedFields: [],
    verticalGeometry: [],
};

const initialKmLengthsSearchState: KmLengthsSearchState = {
    trackNumber: undefined,
    startKm: undefined,
    endKm: undefined,

    validationErrors: [],
    committedFields: [],
    kmLengths: [],
};

const spiralTypes = [GeometryType.CLOTHOID, GeometryType.BIQUADRATIC_PARABOLA];
export const selectedElementTypes = (
    searchGeometry: SearchGeometries,
): GeometryTypeIncludingMissing[] =>
    [
        searchGeometry.searchLines ? GeometryType.LINE : undefined,
        searchGeometry.searchCurves ? GeometryType.CURVE : undefined,
        searchGeometry.searchMissingGeometry ? MissingSection.MISSING_SECTION : undefined,
    ]
        .concat(searchGeometry.searchClothoids ? spiralTypes : [])
        .filter(filterNotEmpty);

const hasAtLeastOneTypeSelected = ({
    searchLines,
    searchCurves,
    searchClothoids,
    searchMissingGeometry,
}: SearchGeometries) => searchLines || searchCurves || searchClothoids || searchMissingGeometry;

export const validTrackMeterOrUndefined = (trackMeterCandidate: string) => {
    if (trackMeterIsValid(trackMeterCandidate)) return trackMeterCandidate;
    else return undefined;
};

const validateContinuousGeometry = (
    state: ElementListContinuousGeometrySearchState,
): ValidationError<ContinuousSearchParameters>[] =>
    [
        validate(hasAtLeastOneTypeSelected(state.searchFields.searchGeometries), {
            field: 'searchGeometries',
            reason: 'no-types-selected',
            type: ValidationErrorType.ERROR,
        }),
        validate(
            state.searchFields.startTrackMeter === '' ||
                trackMeterIsValid(state.searchFields.startTrackMeter),
            {
                field: 'startTrackMeter',
                reason: 'invalid-track-meter',
                type: ValidationErrorType.ERROR,
            },
        ),
        validate(
            !trackMeterIsValid(state.searchFields.endTrackMeter) ||
                !trackMeterIsValid(state.searchFields.startTrackMeter) ||
                compareTrackMeterStrings(
                    state.searchFields.startTrackMeter,
                    state.searchFields.endTrackMeter,
                ) <= 0,
            {
                field: 'endTrackMeter',
                reason: 'end-before-start',
                type: ValidationErrorType.ERROR,
            },
        ),
        validate(
            state.searchFields.endTrackMeter === '' ||
                trackMeterIsValid(state.searchFields.endTrackMeter),
            {
                field: 'endTrackMeter' as keyof ContinuousSearchParameters,
                reason: 'invalid-track-meter',
                type: ValidationErrorType.ERROR,
            },
        ),
    ].filter(filterNotEmpty);

const validateLocationTrackVerticalGeometrySearch = (
    state: LocationTrackVerticalGeometrySearchState,
): ValidationError<LocationTrackVerticalGeometrySearchParameters>[] =>
    [
        validate(
            state.searchFields.startTrackMeter === '' ||
                trackMeterIsValid(state.searchFields.startTrackMeter),
            {
                field: 'startTrackMeter',
                reason: 'invalid-track-meter',
                type: ValidationErrorType.ERROR,
            },
        ),
        validate(
            !trackMeterIsValid(state.searchFields.endTrackMeter) ||
                !trackMeterIsValid(state.searchFields.startTrackMeter) ||
                compareTrackMeterStrings(
                    state.searchFields.startTrackMeter,
                    state.searchFields.endTrackMeter,
                ) <= 0,
            {
                field: 'endTrackMeter',
                reason: 'end-before-start',
                type: ValidationErrorType.ERROR,
            },
        ),
        validate(
            state.searchFields.endTrackMeter === '' ||
                trackMeterIsValid(state.searchFields.endTrackMeter),
            {
                field: 'endTrackMeter' as keyof LocationTrackVerticalGeometrySearchParameters,
                reason: 'invalid-track-meter',
                type: ValidationErrorType.ERROR,
            },
        ),
    ].filter(filterNotEmpty);

export const continuousGeometrySearchSlice = createSlice({
    name: 'continousGeometrySearch',
    initialState: initialContinuousSearchState,
    reducers: {
        onUpdateLocationTrackSearchProp: function <TKey extends keyof ContinuousSearchParameters>(
            state: ElementListContinuousGeometrySearchState,
            { payload: propEdit }: PayloadAction<PropEdit<ContinuousSearchParameters, TKey>>,
        ) {
            state.searchFields[propEdit.key] = propEdit.value;
            if (propEdit.key === 'locationTrack') {
                state.searchParameters['startTrackMeter'] = '';
                state.searchParameters['endTrackMeter'] = '';
                state.searchFields['startTrackMeter'] = '';
                state.searchFields['endTrackMeter'] = '';
            }

            state.validationErrors = validateContinuousGeometry(state);
            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationErrors)) {
                // Valid value entered for a field, mark that field as committed
                state.committedFields = [...state.committedFields, propEdit.key];
            }

            if (state.committedFields.includes(propEdit.key)) {
                state.searchParameters[propEdit.key] = propEdit.value;
            }
        },
        onCommitLocationTrackSearchField: function <TKey extends keyof ContinuousSearchParameters>(
            state: ElementListContinuousGeometrySearchState,
            { payload: key }: PayloadAction<TKey>,
        ) {
            state.committedFields = [...state.committedFields, key];
        },
        onSetLocationTrackElements: function (
            state: ElementListContinuousGeometrySearchState,
            { payload: elements }: PayloadAction<ElementItem[]>,
        ) {
            state.elements = elements;
        },
    },
});

export const planSearchSlice = createSlice({
    name: 'elementList',
    initialState: initialPlanGeometrySearchState,
    reducers: {
        onUpdatePlanSearchProp: function <TKey extends keyof PlanGeometrySearchState>(
            state: PlanGeometrySearchState,
            { payload: propEdit }: PayloadAction<PropEdit<PlanGeometrySearchState, TKey>>,
        ) {
            state[propEdit.key] = propEdit.value;
            state.validationErrors = [
                validate(hasAtLeastOneTypeSelected(state.searchGeometries), {
                    field: 'searchGeometries' as keyof PlanGeometrySearchState,
                    reason: 'no-types-selected',
                    type: ValidationErrorType.ERROR,
                }),
            ].filter(filterNotEmpty);
            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationErrors)) {
                // Valid value entered for a field, mark that field as committed
                state.committedFields = [...state.committedFields, propEdit.key];
            }
        },
        onSetPlanElements: function (
            state: PlanGeometrySearchState,
            { payload: elements }: PayloadAction<ElementItem[]>,
        ) {
            state.elements = elements;
        },
    },
});

export const locationTrackVerticalGeometrySearchSlice = createSlice({
    name: 'locationTrackVerticalGeometrySearch',
    initialState: initialLocationTrackVerticalGeometrySearchState,
    reducers: {
        onUpdateVerticalGeometryLocationTrackSearchProp: function <
            TKey extends keyof LocationTrackVerticalGeometrySearchParameters,
        >(
            state: LocationTrackVerticalGeometrySearchState,
            {
                payload: propEdit,
            }: PayloadAction<PropEdit<LocationTrackVerticalGeometrySearchParameters, TKey>>,
        ) {
            state.searchFields[propEdit.key] = propEdit.value;
            if (propEdit.key === 'locationTrack') {
                state.searchParameters['startTrackMeter'] = '';
                state.searchParameters['endTrackMeter'] = '';
                state.searchFields['startTrackMeter'] = '';
                state.searchFields['endTrackMeter'] = '';
            }

            state.validationErrors = validateLocationTrackVerticalGeometrySearch(state);
            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationErrors)) {
                // Valid value entered for a field, mark that field as committed
                state.committedFields = [...state.committedFields, propEdit.key];
            }

            if (state.committedFields.includes(propEdit.key)) {
                state.searchParameters[propEdit.key] = propEdit.value;
            }
        },
        onCommitVerticalGeometryLocationTrackSearchField: function <
            TKey extends keyof LocationTrackVerticalGeometrySearchParameters,
        >(state: LocationTrackVerticalGeometrySearchState, { payload: key }: PayloadAction<TKey>) {
            state.committedFields = [...state.committedFields, key];
        },
        onSetLocationTrackVerticalGeometry: function (
            state: LocationTrackVerticalGeometrySearchState,
            { payload: verticalGeometry }: PayloadAction<VerticalGeometryItem[]>,
        ) {
            state.verticalGeometry = verticalGeometry;
        },
    },
});

export const planVerticalGeometrySearchSlice = createSlice({
    name: 'planVerticalGeometrySearch',
    initialState: initialPlanVerticalGeometrySearchState,
    reducers: {
        onUpdatePlanVerticalGeometrySearchProp: function <
            TKey extends keyof PlanVerticalGeometrySearchState,
        >(
            state: PlanVerticalGeometrySearchState,
            { payload: propEdit }: PayloadAction<PropEdit<PlanVerticalGeometrySearchState, TKey>>,
        ) {
            state[propEdit.key] = propEdit.value;
            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationErrors)) {
                // Valid value entered for a field, mark that field as committed
                state.committedFields = [...state.committedFields, propEdit.key];
            }
        },
        onSetPlanVerticalGeometry: function (
            state: PlanVerticalGeometrySearchState,
            { payload: verticalGeometry }: PayloadAction<VerticalGeometryItem[]>,
        ) {
            state.verticalGeometry = verticalGeometry;
        },
    },
});

export const kmLengthsSearchSlice = createSlice({
    name: 'kmLengthsSearch',
    initialState: initialKmLengthsSearchState,
    reducers: {
        onUpdateKmLengthsSearchProp: function <TKey extends keyof KmLengthsSearchState>(
            state: KmLengthsSearchState,
            { payload: propEdit }: PayloadAction<PropEdit<KmLengthsSearchState, TKey>>,
        ) {
            state[propEdit.key] = propEdit.value;
            if (propEdit.key === 'trackNumber') {
                state['startKm'] = undefined;
                state['endKm'] = undefined;
                state.committedFields = ['trackNumber'];
            } else {
                state.committedFields = Array.from(
                    new Set([...state.committedFields, propEdit.key]),
                );
            }
            state.validationErrors = [
                validate(
                    !state.startKm ||
                        !state.endKm ||
                        state.endKm.kmNumber >= state.startKm.kmNumber,
                    {
                        field: 'endKm',
                        reason: 'km-end-before-start',
                        type: ValidationErrorType.ERROR,
                    },
                ),
            ].filter(filterNotEmpty);
        },
        onSetKmLengths: function (
            state: KmLengthsSearchState,
            { payload: kmLengths }: PayloadAction<LayoutKmPostLengthDetails[]>,
        ) {
            state.kmLengths = kmLengths;
        },
    },
});

type SelectedSearch = 'PLAN' | 'LOCATION_TRACK';

type DataProductsState = {
    elementList: {
        selectedSearch: SelectedSearch;
        planSearch: PlanGeometrySearchState;
        locationTrackSearch: ElementListContinuousGeometrySearchState;
    };
    verticalGeometry: {
        selectedSearch: SelectedSearch;
        planSearch: PlanVerticalGeometrySearchState;
        locationTrackSearch: LocationTrackVerticalGeometrySearchState;
    };
    kmLenghts: KmLengthsSearchState;
};

const initialDataProductsState: DataProductsState = {
    elementList: {
        selectedSearch: 'LOCATION_TRACK',
        planSearch: initialPlanGeometrySearchState,
        locationTrackSearch: initialContinuousSearchState,
    },
    verticalGeometry: {
        selectedSearch: 'LOCATION_TRACK',
        planSearch: initialPlanVerticalGeometrySearchState,
        locationTrackSearch: initialLocationTrackVerticalGeometrySearchState,
    },
    kmLenghts: initialKmLengthsSearchState,
};

const dataProductsSlice = createSlice({
    name: 'dataProducts',
    initialState: initialDataProductsState,
    reducers: {
        setSelectedElementListSearch: function (
            state: DataProductsState,
            { payload: search }: PayloadAction<SelectedSearch>,
        ) {
            state.elementList.selectedSearch = search;
        },
        setSelectedVerticalGeometrySearch: function (
            state: DataProductsState,
            { payload: search }: PayloadAction<SelectedSearch>,
        ) {
            state.verticalGeometry.selectedSearch = search;
        },
        ...wrapReducers(
            (state: DataProductsState) => state.elementList.locationTrackSearch,
            continuousGeometrySearchSlice.caseReducers,
        ),
        ...wrapReducers(
            (state: DataProductsState) => state.elementList.planSearch,
            planSearchSlice.caseReducers,
        ),
        ...wrapReducers(
            (state: DataProductsState) => state.verticalGeometry.locationTrackSearch,
            locationTrackVerticalGeometrySearchSlice.caseReducers,
        ),
        ...wrapReducers(
            (state: DataProductsState) => state.verticalGeometry.planSearch,
            planVerticalGeometrySearchSlice.caseReducers,
        ),
        ...wrapReducers(
            (state: DataProductsState) => state.kmLenghts,
            kmLengthsSearchSlice.caseReducers,
        ),
    },
});

export const dataProductsReducer = dataProductsSlice.reducer;
export const dataProductsActions = dataProductsSlice.actions;
