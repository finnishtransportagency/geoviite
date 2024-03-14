import { PayloadAction } from '@reduxjs/toolkit';
import {
    AlignmentPoint,
    LayoutLocationTrack,
    LayoutSwitchId,
    LocationTrackDescriptionSuffixMode,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { TrackLayoutState } from 'track-layout/track-layout-slice';
import { TrackMeter } from 'common/common-model';
import { Point } from 'model/geometry';
import { SplitDuplicate } from 'track-layout/layout-location-track-api';
import { getPlanarDistanceUnwrapped } from 'map/layers/utils/layer-utils';

const DUPLICATE_MAX_DISTANCE = 1.0;

type SplitTargetCandidateBase = {
    name: string;
    descriptionBase: string;
    suffixMode: LocationTrackDescriptionSuffixMode;
    duplicateOf?: LocationTrackId;
    location: Point;
    new: boolean;
};

export type FirstSplitTargetCandidate = SplitTargetCandidateBase & {
    type: 'FIRST_SPLIT';
};

export type SplitTargetCandidate = SplitTargetCandidateBase & {
    type: 'SPLIT';
    switchId: LayoutSwitchId;
    distance: number;
};

export type SplittingState = {
    state: 'SETUP' | 'POSTING';
    endLocation: AlignmentPoint;
    originLocationTrack: LayoutLocationTrack;
    allowedSwitches: SwitchOnLocationTrack[];
    startAndEndSwitches: LayoutSwitchId[];
    duplicateTracks: SplitDuplicate[];
    firstSplit: FirstSplitTargetCandidate;
    splits: SplitTargetCandidate[];
    disabled: boolean;
};

export type SplitStart = {
    locationTrack: LayoutLocationTrack;
    allowedSwitches: SwitchOnLocationTrack[];
    startAndEndSwitches: LayoutSwitchId[];
    duplicateTracks: SplitDuplicate[];
    startLocation: AlignmentPoint;
    endLocation: AlignmentPoint;
};

export type SwitchOnLocationTrack = {
    switchId: LayoutSwitchId;
    address: TrackMeter | undefined;
    location: Point | undefined;
    distance: number | undefined;
};

export type SplitRequest = {
    sourceTrackId: LocationTrackId;
    targetTracks: SplitRequestTarget[];
};

export type SplitRequestTarget = {
    duplicateTrackId: LocationTrackId | undefined;
    startAtSwitchId: LayoutSwitchId | undefined;
    name: string;
    descriptionBase: string;
    descriptionSuffix: LocationTrackDescriptionSuffixMode;
};

export const sortSplitsByDistance = (splits: SplitTargetCandidate[]) =>
    [...splits].sort((a, b) => a.distance - b.distance);

const findClosestDuplicate = (duplicates: SplitDuplicate[], otherPoint: Point) =>
    duplicates
        .map((dupe) => ({
            distance: getPlanarDistanceUnwrapped(
                dupe.start.point.x,
                dupe.start.point.y,
                otherPoint.x,
                otherPoint.y,
            ),
            duplicate: dupe,
        }))
        .sort((a, b) => a.distance - b.distance)[0];

export const splitReducers = {
    onStartSplitting: (state: TrackLayoutState, { payload }: PayloadAction<SplitStart>): void => {
        const duplicateTrackClosestToStart = findClosestDuplicate(
            payload.duplicateTracks,
            payload.startLocation,
        );
        state.publishType = 'DRAFT';
        state.splittingState = {
            state: 'SETUP',
            originLocationTrack: payload.locationTrack,
            allowedSwitches: payload.allowedSwitches,
            startAndEndSwitches: payload.startAndEndSwitches,
            duplicateTracks: payload.duplicateTracks,
            splits: [],
            endLocation: payload.endLocation,
            disabled: payload.locationTrack.editState !== 'UNEDITED',
            firstSplit: {
                type: 'FIRST_SPLIT',
                name:
                    duplicateTrackClosestToStart &&
                    duplicateTrackClosestToStart.distance <= DUPLICATE_MAX_DISTANCE
                        ? duplicateTrackClosestToStart.duplicate.name
                        : '',
                duplicateOf:
                    duplicateTrackClosestToStart &&
                    duplicateTrackClosestToStart.distance <= DUPLICATE_MAX_DISTANCE
                        ? duplicateTrackClosestToStart.duplicate.id
                        : undefined,
                descriptionBase: '',
                suffixMode: 'NONE',
                location: payload.startLocation,
                new: true,
            },
        };
    },
    stopSplitting: (state: TrackLayoutState): void => {
        state.splittingState = undefined;
    },
    setDisabled: (state: TrackLayoutState, { payload }: PayloadAction<boolean>): void => {
        if (state.splittingState) {
            state.splittingState.disabled = payload;
        }
    },
    addSplit: (state: TrackLayoutState, { payload }: PayloadAction<LayoutSwitchId>): void => {
        const allowedSwitch = state.splittingState?.allowedSwitches?.find(
            (sw) => sw.switchId == payload,
        );
        if (
            state.splittingState &&
            !state.splittingState.disabled &&
            allowedSwitch?.location &&
            allowedSwitch?.distance &&
            !state.splittingState.splits.some((split) => split.switchId === payload)
        ) {
            const closestDupe = findClosestDuplicate(
                state.splittingState.duplicateTracks,
                allowedSwitch.location,
            );
            state.splittingState.splits = state.splittingState.splits.concat([
                {
                    type: 'SPLIT',
                    switchId: payload,
                    name:
                        closestDupe && closestDupe.distance <= DUPLICATE_MAX_DISTANCE
                            ? closestDupe.duplicate.name
                            : '',
                    duplicateOf:
                        closestDupe && closestDupe.distance <= DUPLICATE_MAX_DISTANCE
                            ? closestDupe.duplicate.id
                            : undefined,
                    descriptionBase: '',
                    suffixMode: 'NONE',
                    location: allowedSwitch.location,
                    distance: allowedSwitch.distance,
                    new: true,
                },
            ]);
        }
    },
    markSplitOld: (
        state: TrackLayoutState,
        { payload }: PayloadAction<LayoutSwitchId | undefined>,
    ): void => {
        if (state.splittingState) {
            if (payload) {
                state.splittingState.splits = state.splittingState.splits.map((split) =>
                    split.switchId === payload ? { ...split, new: false } : split,
                );
            } else {
                state.splittingState.firstSplit = {
                    ...state.splittingState.firstSplit,
                    new: false,
                };
            }
        }
    },
    removeSplit: (state: TrackLayoutState, { payload }: PayloadAction<LayoutSwitchId>): void => {
        if (state.splittingState) {
            state.splittingState.splits = state.splittingState.splits.filter(
                (split) => split.switchId !== payload,
            );
        }
    },
    updateSplit: (
        state: TrackLayoutState,
        { payload }: PayloadAction<SplitTargetCandidate | FirstSplitTargetCandidate>,
    ): void => {
        if (state.splittingState) {
            if (payload.type === 'SPLIT') {
                state.splittingState.splits = state.splittingState.splits
                    .filter((split) => split.switchId !== payload.switchId)
                    .concat([payload]);
            } else {
                state.splittingState.firstSplit = payload;
            }
        }
    },
    startPostingSplit: (state: TrackLayoutState): void => {
        if (state.splittingState) {
            state.splittingState.state = 'POSTING';
        }
    },
    returnToSplitting: (state: TrackLayoutState): void => {
        if (state.splittingState) {
            state.splittingState.state = 'SETUP';
        }
    },
};
