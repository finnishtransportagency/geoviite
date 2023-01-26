import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';
import {
    isPropEditFieldCommitted,
    PropEdit,
    ValidationError,
    ValidationErrorType,
} from 'utils/validation-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { GeometryPlanHeader, GeometryType } from 'geometry/geometry-model';

type SearchGeometries = {
    searchLines: boolean;
    searchCurves: boolean;
    searchClothoids: boolean;
    searchMissingGeometry: boolean;
};

export type PlanGeometrySearchState = {
    plan: GeometryPlanHeader | undefined;
    searchGeometries: SearchGeometries;

    validationErrors: ValidationError<PlanGeometrySearchState>[];
    committedFields: (keyof PlanGeometrySearchState)[];
};

export const initialPlanGeometrySearchState: PlanGeometrySearchState = {
    plan: undefined,
    searchGeometries: {
        searchLines: true,
        searchCurves: true,
        searchClothoids: true,
        searchMissingGeometry: false,
    },

    validationErrors: [],
    committedFields: [],
};

export type ElementListContinuousGeometrySearchState = {
    locationTrack: LayoutLocationTrack | undefined;
    startTrackMeter: string;
    endTrackMeter: string;
    searchGeometries: SearchGeometries;

    validationErrors: ValidationError<ElementListContinuousGeometrySearchState>[];
    committedFields: (keyof ElementListContinuousGeometrySearchState)[];
};

export const initialContinuousSearchState: ElementListContinuousGeometrySearchState = {
    locationTrack: undefined,
    startTrackMeter: '',
    endTrackMeter: '',
    searchGeometries: {
        searchLines: true,
        searchCurves: true,
        searchClothoids: true,
        searchMissingGeometry: true,
    },

    validationErrors: [],
    committedFields: [],
};

export const selectedElementTypes = (searchGeometry: SearchGeometries) =>
    [
        searchGeometry.searchLines ? GeometryType.LINE : undefined,
        searchGeometry.searchCurves ? GeometryType.CURVE : undefined,
        searchGeometry.searchClothoids ? GeometryType.CLOTHOID : undefined,
    ].filter(filterNotEmpty);

const TRACK_METER_REGEX = /([0-9]{1,4})\+([0-9]{4})$/g;

const hasAtLeastOneTypeSelected = ({
    searchLines,
    searchCurves,
    searchClothoids,
    searchMissingGeometry,
}: SearchGeometries) => searchLines || searchCurves || searchClothoids || searchMissingGeometry;

const trackMeterIsValid = (trackMeter: string) => TRACK_METER_REGEX.test(trackMeter);

const validaterosRamazotti = (
    state: ElementListContinuousGeometrySearchState,
): ValidationError<ElementListContinuousGeometrySearchState>[] =>
    [
        hasAtLeastOneTypeSelected(state.searchGeometries)
            ? undefined
            : {
                  field: 'searchGeometries' as keyof ElementListContinuousGeometrySearchState,
                  reason: 'no-geometry-selected',
                  type: ValidationErrorType.ERROR,
              },
        trackMeterIsValid(state.startTrackMeter)
            ? undefined
            : {
                  field: 'startTrackMeter' as keyof ElementListContinuousGeometrySearchState,
                  reason: 'invalid-track-meter',
                  type: ValidationErrorType.ERROR,
              },
        trackMeterIsValid(state.endTrackMeter)
            ? undefined
            : {
                  field: 'endTrackMeter' as keyof ElementListContinuousGeometrySearchState,
                  reason: 'invalid-track-meter',
                  type: ValidationErrorType.ERROR,
              },
    ].filter(filterNotEmpty);

const continuousGeometrySearchSlice = createSlice({
    name: 'continousGeometrySearch',
    initialState: initialContinuousSearchState,
    reducers: {
        onUpdateProp: function <TKey extends keyof ElementListContinuousGeometrySearchState>(
            state: ElementListContinuousGeometrySearchState,
            {
                payload: propEdit,
            }: PayloadAction<PropEdit<ElementListContinuousGeometrySearchState, TKey>>,
        ) {
            state[propEdit.key] = propEdit.value;
            state.validationErrors = validaterosRamazotti(state);
            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationErrors)) {
                // Valid value entered for a field, mark that field as committed
                state.committedFields = [...state.committedFields, propEdit.key];
            }
        },
        onCommitField: function <TKey extends keyof ElementListContinuousGeometrySearchState>(
            state: ElementListContinuousGeometrySearchState,
            { payload: key }: PayloadAction<TKey>,
        ) {
            state.committedFields = [...state.committedFields, key];
        },
    },
});

const planSearchSlice = createSlice({
    name: 'elementList',
    initialState: initialPlanGeometrySearchState,
    reducers: {
        onUpdateProp: function <TKey extends keyof PlanGeometrySearchState>(
            state: PlanGeometrySearchState,
            { payload: propEdit }: PayloadAction<PropEdit<PlanGeometrySearchState, TKey>>,
        ) {
            state[propEdit.key] = propEdit.value;
            const anySelected = hasAtLeastOneTypeSelected(state.searchGeometries)
                ? []
                : [
                      {
                          field: 'searchGeometries' as keyof PlanGeometrySearchState,
                          reason: 'no-geometry-selected',
                          type: ValidationErrorType.ERROR,
                      },
                  ];
            state.validationErrors = anySelected;
            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationErrors)) {
                // Valid value entered for a field, mark that field as committed
                state.committedFields = [...state.committedFields, propEdit.key];
            }
        },
    },
});

export const continuousGeometryReducer = continuousGeometrySearchSlice.reducer;
export const continuousGeometryActions = continuousGeometrySearchSlice.actions;

export const planSearchReducer = planSearchSlice.reducer;
export const planSearchActions = planSearchSlice.actions;
