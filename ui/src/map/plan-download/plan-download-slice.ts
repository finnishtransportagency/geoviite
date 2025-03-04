import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import {
    FieldValidationIssue,
    FieldValidationIssueType,
    isPropEditFieldCommitted,
    PropEdit,
    validate,
} from 'utils/validation-utils';
import { compareTrackMeterStrings, trackMeterIsValid } from 'common/common-model';
import { filterNotEmpty } from 'utils/array-utils';
import { GeometryPlanId, KmNumberRange, PlanApplicability } from 'geometry/geometry-model';

export type AreaSelection = {
    trackNumber: LayoutTrackNumberId | undefined;
    locationTrack: LocationTrackId | undefined;
    startTrackMeter: string;
    endTrackMeter: string;
};

export type PlanSelectionType = 'AREA' | 'PLAN';
export type PlanDownloadState = {
    selectionType: PlanSelectionType;
    areaSelection: AreaSelection;
    validationIssues: FieldValidationIssue<AreaSelection>[];
    plans: DownloadablePlan[];
    selectedApplicabilities: PlanApplicability[];
    committedFields: (keyof AreaSelection)[];
};

export type DownloadablePlan = {
    id: GeometryPlanId;
    name: string;
    selected: boolean;
    applicability?: PlanApplicability;
    kmNumberRange: KmNumberRange;
};

const DUMMY_PLANS: DownloadablePlan[] = [
    {
        id: 'INT_1',
        name: 'HOJOOOOOO',
        selected: true,
        applicability: 'STATISTICS',
        kmNumberRange: { min: '0004', max: '0100' },
    },
    {
        id: 'INT_2',
        name: 'HOJOOOOOOOO',
        selected: false,
        applicability: 'MAINTENANCE',
        kmNumberRange: { min: '0002', max: '0100' },
    },
    {
        id: 'INT_3',
        name: 'HOJOOOOOOOOOO',
        selected: true,
        applicability: 'PLANNING',
        kmNumberRange: { min: '0003', max: '0100' },
    },
    {
        id: 'INT_4',
        name: 'HOJOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO',
        selected: false,
        applicability: undefined,
        kmNumberRange: { min: '0005', max: '0100' },
    },
];

export const initialPlanDownloadStateFromSelection = (
    locationTrackId: LocationTrackId | undefined,
    trackNumberId: LayoutTrackNumberId | undefined,
): PlanDownloadState => ({
    ...initialPlanDownloadState,
    areaSelection: {
        ...initialPlanDownloadState.areaSelection,
        locationTrack: locationTrackId,
        trackNumber: trackNumberId,
    },
});

export const initialPlanDownloadState: PlanDownloadState = {
    selectionType: 'AREA',
    areaSelection: {
        trackNumber: undefined,
        locationTrack: undefined,
        startTrackMeter: '',
        endTrackMeter: '',
    },
    plans: DUMMY_PLANS,
    selectedApplicabilities: ['PLANNING', 'MAINTENANCE', 'STATISTICS'],
    validationIssues: [],
    committedFields: [],
};

const validateAreaSelection = (state: PlanDownloadState): FieldValidationIssue<AreaSelection>[] =>
    state.selectionType === 'AREA'
        ? [
              validate(
                  state.areaSelection.trackNumber !== undefined ||
                      state.areaSelection.locationTrack !== undefined,
                  {
                      type: FieldValidationIssueType.ERROR,
                      field: 'trackNumber',
                      reason: 'mandatory-field',
                  },
              ),
              validate(
                  !state.areaSelection.startTrackMeter ||
                      trackMeterIsValid(state.areaSelection.startTrackMeter),
                  {
                      type: FieldValidationIssueType.ERROR,
                      field: 'startTrackMeter',
                      reason: 'invalid-track-meter',
                  },
              ),
              validate(
                  !state.areaSelection.endTrackMeter ||
                      trackMeterIsValid(state.areaSelection.endTrackMeter),
                  {
                      type: FieldValidationIssueType.ERROR,
                      field: 'endTrackMeter',
                      reason: 'invalid-track-meter',
                  },
              ),
              validate(
                  !trackMeterIsValid(state.areaSelection.endTrackMeter) ||
                      !trackMeterIsValid(state.areaSelection.startTrackMeter) ||
                      compareTrackMeterStrings(
                          state.areaSelection.startTrackMeter,
                          state.areaSelection.endTrackMeter,
                      ) <= 0,
                  {
                      field: 'endTrackMeter',
                      reason: 'end-before-start',
                      type: FieldValidationIssueType.ERROR,
                  },
              ),
          ].filter(filterNotEmpty)
        : [];

const planDownloadSlice = createSlice({
    name: 'planDownload',
    initialState: initialPlanDownloadState,
    reducers: {
        setSelectionType: function (
            state: PlanDownloadState,
            { payload: type }: PayloadAction<PlanSelectionType>,
        ) {
            state.selectionType = type;
        },
        onUpdateProp: function <TKey extends keyof AreaSelection>(
            state: PlanDownloadState,
            { payload: propEdit }: PayloadAction<PropEdit<AreaSelection, TKey>>,
        ) {
            state.areaSelection[propEdit.key] = propEdit.value;
            state.validationIssues = validateAreaSelection(state);

            if (isPropEditFieldCommitted(propEdit, state.committedFields, state.validationIssues)) {
                // Valid value entered for a field, mark that field as committed
                state.committedFields = [...state.committedFields, propEdit.key];
            }
        },
        onCommitField: function <TKey extends keyof AreaSelection>(
            state: PlanDownloadState,
            { payload: key }: PayloadAction<TKey>,
        ) {
            state.committedFields = [...state.committedFields, key];
        },
        setApplicabilities: function (
            state: PlanDownloadState,
            { payload: applicabilities }: PayloadAction<PlanApplicability[]>,
        ) {
            state.selectedApplicabilities = applicabilities;
        },
        setPlanSelected: function (
            state: PlanDownloadState,
            { payload: planId }: PayloadAction<{ id: GeometryPlanId; selected: boolean }>,
        ) {
            state.plans = state.plans.map((plan) =>
                plan.id === planId.id ? { ...plan, selected: planId.selected } : plan,
            );
        },
    },
});

export const planDownloadReducer = planDownloadSlice.reducer;
export const planDownloadActions = planDownloadSlice.actions;
