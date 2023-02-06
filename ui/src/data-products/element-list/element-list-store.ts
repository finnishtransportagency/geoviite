import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import {
    isPropEditFieldCommitted,
    PropEdit,
    validate,
    ValidationError,
    ValidationErrorType,
} from 'utils/validation-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { ElementItem, GeometryPlanHeader, GeometryType } from 'geometry/geometry-model';
import { trackMeterIsValid } from 'common/common-model';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';

type SearchGeometries = {
    searchLines: boolean;
    searchCurves: boolean;
    searchClothoids: boolean;
    searchMissingGeometry: boolean;
};

export type PlanGeometrySearchState = {
    plan: GeometryPlanHeader | undefined;
    searchGeometries: SearchGeometries;

    elements: ElementItem[];
    validationErrors: ValidationError<PlanGeometrySearchState>[];
    committedFields: (keyof PlanGeometrySearchState)[];
};

enum MissingSection {
    MISSING_SECTION = 'MISSING_SECTION',
}

export type GeometryTypeIncludingMissing = GeometryType | MissingSection;

export const initialPlanGeometrySearchState: PlanGeometrySearchState = {
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

export const selectedElementTypes = (
    searchGeometry: SearchGeometries,
): GeometryTypeIncludingMissing[] =>
    [
        searchGeometry.searchLines ? GeometryType.LINE : undefined,
        searchGeometry.searchCurves ? GeometryType.CURVE : undefined,
        searchGeometry.searchMissingGeometry ? MissingSection.MISSING_SECTION : undefined,
    ]
        .concat(
            searchGeometry.searchClothoids
                ? [GeometryType.CLOTHOID, GeometryType.BIQUADRATIC_PARABOLA]
                : [],
        )
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
            state.searchFields.endTrackMeter === '' ||
                trackMeterIsValid(state.searchFields.endTrackMeter),
            {
                field: 'endTrackMeter' as keyof ContinuousSearchParameters,
                reason: 'invalid-track-meter',
                type: ValidationErrorType.ERROR,
            },
        ),
    ].filter(filterNotEmpty);

const continuousGeometrySearchSlice = createSlice({
    name: 'continousGeometrySearch',
    initialState: initialContinuousSearchState,
    reducers: {
        onUpdateProp: function <TKey extends keyof ContinuousSearchParameters>(
            state: ElementListContinuousGeometrySearchState,
            { payload: propEdit }: PayloadAction<PropEdit<ContinuousSearchParameters, TKey>>,
        ) {
            state.searchFields[propEdit.key] = propEdit.value;
            state.validationErrors = validateContinuousGeometry(state);
            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationErrors)) {
                // Valid value entered for a field, mark that field as committed
                state.committedFields = [...state.committedFields, propEdit.key];
            }

            if (state.committedFields.includes(propEdit.key)) {
                state.searchParameters[propEdit.key] = propEdit.value;
            }
        },
        onCommitField: function <TKey extends keyof ContinuousSearchParameters>(
            state: ElementListContinuousGeometrySearchState,
            { payload: key }: PayloadAction<TKey>,
        ) {
            state.committedFields = [...state.committedFields, key];
        },
        onSetElements: function (
            state: ElementListContinuousGeometrySearchState,
            { payload: elements }: PayloadAction<ElementItem[]>,
        ) {
            state.elements = elements;
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
        onSetElements: function (
            state: PlanGeometrySearchState,
            { payload: elements }: PayloadAction<ElementItem[]>,
        ) {
            state.elements = elements;
        },
    },
});

export const continuousGeometryReducer = continuousGeometrySearchSlice.reducer;
export const continuousGeometryActions = continuousGeometrySearchSlice.actions;

export const planSearchReducer = planSearchSlice.reducer;
export const planSearchActions = planSearchSlice.actions;
