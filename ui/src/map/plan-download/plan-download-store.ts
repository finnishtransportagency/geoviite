import { PayloadAction } from '@reduxjs/toolkit';
import {
    AlignmentStartAndEnd,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import {
    FieldValidationIssue,
    FieldValidationIssueType,
    isPropEditFieldCommitted,
    PropEdit,
    validate,
} from 'utils/validation-utils';
import {
    compareKmNumberStrings,
    isKmNumberWithinRange,
    kmNumberIsValid,
} from 'common/common-model';
import { filterNotEmpty } from 'utils/array-utils';
import {
    GeometryPlanId,
    KmNumberRange,
    PlanApplicability,
    PlanSource,
} from 'geometry/geometry-model';
import { isValidKmNumber } from 'tool-panel/km-post/dialog/km-post-edit-store';

export type AreaSelection = {
    trackNumber: LayoutTrackNumberId | undefined;
    locationTrack: LocationTrackId | undefined;
    startTrackMeter: string;
    endTrackMeter: string;
    alignmentStartAndEnd: AlignmentStartAndEnd | undefined;
};

export type PopupSection = 'AREA' | 'PLAN';
export type PlanDownloadState = {
    openPopupSection: PopupSection | undefined;
    areaSelection: AreaSelection;
    validationIssues: FieldValidationIssue<AreaSelection>[];
    selectedPlans: GeometryPlanId[];
    selectedApplicabilities: PlanApplicability[];
    includingPaikannuspalvelu: boolean;
    committedFields: (keyof AreaSelection)[];
};

export type DownloadablePlan = {
    id: GeometryPlanId;
    name: string;
    applicability?: PlanApplicability;
    source: PlanSource;
    kmNumberRange: KmNumberRange | undefined;
};

export type SelectedPlanDownloadAsset =
    | {
          id: LayoutTrackNumberId;
          type: 'TRACK_NUMBER';
      }
    | { id: LocationTrackId; type: 'LOCATION_TRACK' };

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
    openPopupSection: 'AREA',
    areaSelection: {
        trackNumber: undefined,
        locationTrack: undefined,
        startTrackMeter: '',
        endTrackMeter: '',
        alignmentStartAndEnd: undefined,
    },
    selectedPlans: [],
    selectedApplicabilities: ['PLANNING', 'MAINTENANCE', 'STATISTICS'],
    includingPaikannuspalvelu: false,
    validationIssues: [],
    committedFields: [],
};

const validateAreaSelection = (state: PlanDownloadState): FieldValidationIssue<AreaSelection>[] => {
    const alignmentStartAndEndRange: KmNumberRange | undefined =
        state.areaSelection.alignmentStartAndEnd?.start?.address &&
        state.areaSelection.alignmentStartAndEnd.end?.address
            ? {
                  min: state.areaSelection.alignmentStartAndEnd.start.address.kmNumber,
                  max: state.areaSelection.alignmentStartAndEnd.end.address.kmNumber,
              }
            : undefined;
    const alignmentType = state.areaSelection.locationTrack ? 'location-track' : 'track-number';

    return state.openPopupSection === 'AREA'
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
                  state.areaSelection.startTrackMeter === '' ||
                      kmNumberIsValid(state.areaSelection.startTrackMeter),
                  {
                      type: FieldValidationIssueType.ERROR,
                      field: 'startTrackMeter',
                      reason: 'invalid-track-meter',
                  },
              ),
              validate(
                  state.areaSelection.endTrackMeter === '' ||
                      kmNumberIsValid(state.areaSelection.endTrackMeter),
                  {
                      type: FieldValidationIssueType.ERROR,
                      field: 'endTrackMeter',
                      reason: 'invalid-track-meter',
                  },
              ),
              validate(
                  !kmNumberIsValid(state.areaSelection.endTrackMeter) ||
                      !kmNumberIsValid(state.areaSelection.startTrackMeter) ||
                      compareKmNumberStrings(
                          state.areaSelection.startTrackMeter,
                          state.areaSelection.endTrackMeter,
                      ) <= 0,
                  {
                      field: 'endTrackMeter',
                      reason: 'end-before-start',
                      type: FieldValidationIssueType.ERROR,
                  },
              ),
              validate(
                  !state.areaSelection.alignmentStartAndEnd ||
                      !alignmentStartAndEndRange ||
                      !isValidKmNumber(state.areaSelection.startTrackMeter) ||
                      isKmNumberWithinRange(
                          state.areaSelection.startTrackMeter,
                          alignmentStartAndEndRange,
                      ),
                  {
                      field: 'startTrackMeter',
                      reason: `outside-${alignmentType}-range`,
                      params: {
                          start: alignmentStartAndEndRange?.min,
                          end: alignmentStartAndEndRange?.max,
                      },
                      type: FieldValidationIssueType.WARNING,
                  },
              ),
              validate(
                  !state.areaSelection.alignmentStartAndEnd ||
                      !alignmentStartAndEndRange ||
                      !isValidKmNumber(state.areaSelection.endTrackMeter) ||
                      isKmNumberWithinRange(
                          state.areaSelection.endTrackMeter,
                          alignmentStartAndEndRange,
                      ),
                  {
                      field: 'endTrackMeter',
                      reason: `outside-${alignmentType}-range`,
                      params: {
                          start: alignmentStartAndEndRange?.min,
                          end: alignmentStartAndEndRange?.max,
                      },
                      type: FieldValidationIssueType.WARNING,
                  },
              ),
          ].filter(filterNotEmpty)
        : [];
};

export const planDownloadReducers = {
    setOpenPopupSection: function (
        state: PlanDownloadState,
        { payload: type }: PayloadAction<PopupSection | undefined>,
    ) {
        state.openPopupSection = type;
    },
    onUpdatePlanDownloadAreaSelectionProp: function <TKey extends keyof AreaSelection>(
        state: PlanDownloadState,
        { payload: propEdit }: PayloadAction<PropEdit<AreaSelection, TKey>>,
    ) {
        state.areaSelection[propEdit.key] = propEdit.value;
        state.validationIssues = validateAreaSelection(state);

        if (
            isPropEditFieldCommitted(
                propEdit,
                state.committedFields,
                state.validationIssues.filter(
                    (issue) => issue.type === FieldValidationIssueType.ERROR,
                ),
            )
        ) {
            // Valid value entered for a field, mark that field as committed
            state.committedFields = [...state.committedFields, propEdit.key];
        }
    },
    onCommitPlanDownloadAreaSelectionField: function <TKey extends keyof AreaSelection>(
        state: PlanDownloadState,
        { payload: key }: PayloadAction<TKey>,
    ) {
        state.committedFields = [...state.committedFields, key];
    },
    setPlanDownloadApplicabilities: function (
        state: PlanDownloadState,
        { payload: applicabilities }: PayloadAction<PlanApplicability[]>,
    ) {
        state.selectedApplicabilities = applicabilities;
    },
    setIncludingPaikannuspalvelu: function (
        state: PlanDownloadState,
        { payload: includingPaikannuspalvelu }: PayloadAction<boolean>,
    ) {
        state.includingPaikannuspalvelu = includingPaikannuspalvelu;
    },
    togglePlanForDownload: function (
        state: PlanDownloadState,
        { payload: planId }: PayloadAction<{ id: GeometryPlanId; selected: boolean }>,
    ) {
        state.selectedPlans = planId.selected
            ? [...state.selectedPlans, planId.id]
            : state.selectedPlans.filter((p) => p !== planId.id);
    },
    selectMultiplePlansForDownload: function (
        state: PlanDownloadState,
        { payload: selectedPlans }: PayloadAction<GeometryPlanId[]>,
    ) {
        state.selectedPlans = selectedPlans;
    },
    unselectPlansForDownload: function (state: PlanDownloadState) {
        state.selectedPlans = [];
    },
    setPlanDownloadAlignmentStartAndEnd: function (
        state: PlanDownloadState,
        { payload: startAndEnd }: PayloadAction<AlignmentStartAndEnd | undefined>,
    ) {
        state.areaSelection.alignmentStartAndEnd = startAndEnd;
        state.validationIssues = validateAreaSelection(state);
    },
};
