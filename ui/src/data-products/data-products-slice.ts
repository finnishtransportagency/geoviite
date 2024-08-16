import { ActionReducerMapBuilder, createSlice, PayloadAction } from '@reduxjs/toolkit';
import {
    isPropEditFieldCommitted,
    PropEdit,
    validate,
    FieldValidationIssue,
    FieldValidationIssueType,
} from 'utils/validation-utils';
import { filterNotEmpty } from 'utils/array-utils';
import {
    ElementItem,
    GeometryPlanHeader,
    GeometryType,
    PlanSource,
    VerticalGeometryItem,
} from 'geometry/geometry-model';
import { compareTrackMeterStrings, KmNumber, trackMeterIsValid } from 'common/common-model';
import {
    LayoutKmLengthDetails,
    LayoutLocationTrack,
    LayoutTrackNumber,
} from 'track-layout/track-layout-model';
import { wrapReducers } from 'store/store-utils';
import { PURGE } from 'redux-persist';

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
    validationIssues: FieldValidationIssue<PlanGeometrySearchState>[];
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

    validationIssues: FieldValidationIssue<LocationTrackVerticalGeometrySearchParameters>[];
    committedFields: (keyof LocationTrackVerticalGeometrySearchParameters)[];
    verticalGeometry: VerticalGeometryItem[];
};

export type PlanVerticalGeometrySearchState = {
    source: PlanSource;
    plan: GeometryPlanHeader | undefined;

    validationIssues: FieldValidationIssue<PlanVerticalGeometrySearchState>[];
    committedFields: (keyof PlanVerticalGeometrySearchState)[];
    verticalGeometry: VerticalGeometryItem[];
};

export type SelectedKmLengthsSearch = 'TRACK_NUMBER' | 'ENTIRE_RAIL_NETWORK';
export type KmLengthsLocationPrecision = 'PRECISE_LOCATION' | 'APPROXIMATION_IN_LAYOUT';

export type KmLengthsSearchState = {
    trackNumber: LayoutTrackNumber | undefined;
    startKm: KmNumber | undefined;
    endKm: KmNumber | undefined;

    validationIssues: FieldValidationIssue<KmLengthsSearchState>[];
    committedFields: (keyof KmLengthsSearchState)[];
    kmLengths: LayoutKmLengthDetails[];

    selectedSearch: SelectedKmLengthsSearch;
    locationPrecision: KmLengthsLocationPrecision;
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
    validationIssues: [],
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
    validationIssues: FieldValidationIssue<ContinuousSearchParameters>[];
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
    validationIssues: [],
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
    validationIssues: [],
    committedFields: [],
    verticalGeometry: [],
};

const initialPlanVerticalGeometrySearchState: PlanVerticalGeometrySearchState = {
    plan: undefined,
    source: 'GEOMETRIAPALVELU',
    validationIssues: [],
    committedFields: [],
    verticalGeometry: [],
};

const initialKmLengthsSearchState: KmLengthsSearchState = {
    selectedSearch: 'TRACK_NUMBER',

    trackNumber: undefined,
    startKm: undefined,
    endKm: undefined,

    validationIssues: [],
    committedFields: [],
    kmLengths: [],
    locationPrecision: 'PRECISE_LOCATION',
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
): FieldValidationIssue<ContinuousSearchParameters>[] =>
    [
        validate(hasAtLeastOneTypeSelected(state.searchFields.searchGeometries), {
            field: 'searchGeometries',
            reason: 'no-types-selected',
            type: FieldValidationIssueType.ERROR,
        }),
        validate(
            state.searchFields.startTrackMeter === '' ||
                trackMeterIsValid(state.searchFields.startTrackMeter),
            {
                field: 'startTrackMeter',
                reason: 'invalid-track-meter',
                type: FieldValidationIssueType.ERROR,
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
                type: FieldValidationIssueType.ERROR,
            },
        ),
        validate(
            state.searchFields.endTrackMeter === '' ||
                trackMeterIsValid(state.searchFields.endTrackMeter),
            {
                field: 'endTrackMeter' as keyof ContinuousSearchParameters,
                reason: 'invalid-track-meter',
                type: FieldValidationIssueType.ERROR,
            },
        ),
    ].filter(filterNotEmpty);

const validateLocationTrackVerticalGeometrySearch = (
    state: LocationTrackVerticalGeometrySearchState,
): FieldValidationIssue<LocationTrackVerticalGeometrySearchParameters>[] =>
    [
        validate(
            state.searchFields.startTrackMeter === '' ||
                trackMeterIsValid(state.searchFields.startTrackMeter),
            {
                field: 'startTrackMeter',
                reason: 'invalid-track-meter',
                type: FieldValidationIssueType.ERROR,
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
                type: FieldValidationIssueType.ERROR,
            },
        ),
        validate(
            state.searchFields.endTrackMeter === '' ||
                trackMeterIsValid(state.searchFields.endTrackMeter),
            {
                field: 'endTrackMeter' as keyof LocationTrackVerticalGeometrySearchParameters,
                reason: 'invalid-track-meter',
                type: FieldValidationIssueType.ERROR,
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

            state.validationIssues = validateContinuousGeometry(state);
            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationIssues)) {
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
            state.validationIssues = [
                validate(hasAtLeastOneTypeSelected(state.searchGeometries), {
                    field: 'searchGeometries' as keyof PlanGeometrySearchState,
                    reason: 'no-types-selected',
                    type: FieldValidationIssueType.ERROR,
                }),
            ].filter(filterNotEmpty);
            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationIssues)) {
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

            state.validationIssues = validateLocationTrackVerticalGeometrySearch(state);
            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationIssues)) {
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
            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationIssues)) {
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
            state.validationIssues = [
                validate(!state.startKm || !state.endKm || state.endKm >= state.startKm, {
                    field: 'endKm',
                    reason: 'km-end-before-start',
                    type: FieldValidationIssueType.ERROR,
                }),
            ].filter(filterNotEmpty);
        },
        onSetKmLengths: function (
            state: KmLengthsSearchState,
            { payload: kmLengths }: PayloadAction<LayoutKmLengthDetails[]>,
        ) {
            state.kmLengths = kmLengths;
        },
    },
});

export type SelectedGeometrySearch = 'PLAN' | 'LOCATION_TRACK' | 'ENTIRE_RAIL_NETWORK';

export type DataProductsState = {
    elementList: {
        selectedSearch: SelectedGeometrySearch;
        planSearch: PlanGeometrySearchState;
        locationTrackSearch: ElementListContinuousGeometrySearchState;
    };
    verticalGeometry: {
        selectedSearch: SelectedGeometrySearch;
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
    extraReducers: (builder: ActionReducerMapBuilder<DataProductsState>) => {
        builder.addCase(PURGE, (_state, _action) => {
            return initialDataProductsState;
        });
    },
    reducers: {
        setSelectedElementListSearch: function (
            state: DataProductsState,
            { payload: search }: PayloadAction<SelectedGeometrySearch>,
        ) {
            state.elementList.selectedSearch = search;
        },
        setSelectedVerticalGeometrySearch: function (
            state: DataProductsState,
            { payload: search }: PayloadAction<SelectedGeometrySearch>,
        ) {
            state.verticalGeometry.selectedSearch = search;
        },
        setSelectedKmLengthsSearch: function (
            state: DataProductsState,
            { payload: search }: PayloadAction<SelectedKmLengthsSearch>,
        ) {
            state.kmLenghts.selectedSearch = search;
        },
        setKmLengthsLocationPrecision: (
            state: DataProductsState,
            { payload: precision }: PayloadAction<KmLengthsLocationPrecision>,
        ) => {
            state.kmLenghts.locationPrecision = precision;
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
