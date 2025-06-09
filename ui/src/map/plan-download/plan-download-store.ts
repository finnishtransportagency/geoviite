import { PayloadAction } from '@reduxjs/toolkit';
import {
    AlignmentStartAndEnd,
    LayoutLocationTrack,
    LayoutTrackNumber,
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
import { compareKmNumberStrings, isKmNumberWithinRange, KmNumber } from 'common/common-model';
import { filterNotEmpty } from 'utils/array-utils';
import {
    GeometryPlanId,
    KmNumberRange,
    PlanApplicability,
    PlanSource,
} from 'geometry/geometry-model';
import { brand } from 'common/brand';
import { ToolPanelAsset } from 'tool-panel/tool-panel';
import { exhaustiveMatchingGuard, isNil } from 'utils/type-utils';

export type AreaSelection = {
    asset: PlanDownloadAssetId | undefined;
    startTrackMeter: KmNumber | undefined;
    endTrackMeter: KmNumber | undefined;
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

export enum PlanDownloadAssetType {
    TRACK_NUMBER = 'TRACK_NUMBER',
    LOCATION_TRACK = 'LOCATION_TRACK',
}

export type PlanDownloadAssetId =
    | {
          id: LayoutTrackNumberId;
          type: PlanDownloadAssetType.TRACK_NUMBER;
      }
    | { id: LocationTrackId; type: PlanDownloadAssetType.LOCATION_TRACK };

type WithExtremities = {
    startAndEnd: AlignmentStartAndEnd;
};
export type TrackNumberAsset = {
    asset: LayoutTrackNumber;
    type: PlanDownloadAssetType.TRACK_NUMBER;
};
export type TrackNumberAssetAndExtremities = TrackNumberAsset & WithExtremities;

export type LocationTrackAsset = {
    asset: LayoutLocationTrack;
    type: PlanDownloadAssetType.LOCATION_TRACK;
};
export type LocationTrackAssetAndExtremities = LocationTrackAsset & WithExtremities;

export type PlanDownloadAssetAndExtremities =
    | TrackNumberAssetAndExtremities
    | LocationTrackAssetAndExtremities;
export type PlanDownloadAsset = TrackNumberAsset | LocationTrackAsset;

export const initialPlanDownloadStateFromSelection = (
    selectedAsset: PlanDownloadAssetId | undefined,
): PlanDownloadState => {
    return {
        ...initialPlanDownloadState,
        areaSelection: {
            ...initialPlanDownloadState.areaSelection,
            asset: selectedAsset,
        },
    };
};

export const planDownloadAssetIdFromToolPanelAsset = (
    selectedAsset: ToolPanelAsset,
): PlanDownloadAssetId | undefined => {
    switch (selectedAsset.type) {
        case 'TRACK_NUMBER':
            return {
                type: PlanDownloadAssetType.TRACK_NUMBER,
                id: brand(selectedAsset.id),
            };
        case 'LOCATION_TRACK':
            return {
                type: PlanDownloadAssetType.LOCATION_TRACK,
                id: brand(selectedAsset.id),
            };
        case 'GEOMETRY_PLAN':
        case 'GEOMETRY_ALIGNMENT':
        case 'GEOMETRY_KM_POST':
        case 'GEOMETRY_SWITCH':
        case 'SUGGESTED_SWITCH':
        case 'KM_POST':
        case 'SWITCH':
            return undefined;
        default:
            return exhaustiveMatchingGuard(selectedAsset.type);
    }
};

export const initialPlanDownloadState: PlanDownloadState = {
    openPopupSection: 'AREA',
    areaSelection: {
        asset: undefined,
        startTrackMeter: undefined,
        endTrackMeter: undefined,
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
    const alignmentType =
        state.areaSelection.asset?.type === PlanDownloadAssetType.LOCATION_TRACK
            ? 'location-track'
            : state.areaSelection.asset?.type === PlanDownloadAssetType.TRACK_NUMBER
              ? 'track-number'
              : undefined;

    return state.openPopupSection === 'AREA'
        ? [
              validate(state.areaSelection.asset !== undefined, {
                  type: FieldValidationIssueType.ERROR,
                  field: 'asset',
                  reason: 'mandatory-field',
              }),
              validate(
                  isNil(state.areaSelection.startTrackMeter) ||
                      isNil(state.areaSelection.endTrackMeter) ||
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
                      isNil(state.areaSelection.startTrackMeter) ||
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
                      isNil(state.areaSelection.endTrackMeter) ||
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

        // start/endTrackMeter are no longer valid if asset is changed
        if (propEdit.key === 'asset') {
            state.areaSelection.startTrackMeter = undefined;
            state.areaSelection.endTrackMeter = undefined;
        }

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
