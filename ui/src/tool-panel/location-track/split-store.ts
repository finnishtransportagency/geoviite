import { PayloadAction } from '@reduxjs/toolkit';
import {
    LayoutLocationTrack,
    LayoutPoint,
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

export type InitialSplit = {
    name: string;
    descriptionBase: string;
    suffixMode: LocationTrackDescriptionSuffixMode;
    duplicateOf?: LocationTrackId;
    location: Point;
};

export type Split = InitialSplit & {
    switchId: LayoutSwitchId;
    distance: number;
};

export type SplittingState = {
    endLocation: LayoutPoint;
    originLocationTrack: LayoutLocationTrack;
    allowedSwitches: SwitchOnLocationTrack[];
    duplicateTracks: SplitDuplicate[];
    initialSplit: InitialSplit;
    splits: Split[];
};

type SplitStart = {
    locationTrack: LayoutLocationTrack;
    allowedSwitches: SwitchOnLocationTrack[];
    duplicateTracks: SplitDuplicate[];
    startLocation: LayoutPoint;
    endLocation: LayoutPoint;
};

export type SwitchOnLocationTrack = {
    switchId: LayoutSwitchId;
    address: TrackMeter | undefined;
    location: Point | undefined;
    distance: number | undefined;
};

export const sortSplitsByDistance = (splits: Split[]) =>
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
            originLocationTrack: payload.locationTrack,
            allowedSwitches: payload.allowedSwitches,
            duplicateTracks: payload.duplicateTracks,
            splits: [],
            endLocation: payload.endLocation,
            initialSplit: {
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
            },
        };
    },
    cancelSplitting: (state: TrackLayoutState): void => {
        state.splittingState = undefined;
    },
    addSplit: (state: TrackLayoutState, { payload }: PayloadAction<LayoutSwitchId>): void => {
        const allowedSwitch = state.splittingState?.allowedSwitches?.find(
            (sw) => sw.switchId == payload,
        );
        if (
            state.splittingState &&
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
                },
            ]);
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
        { payload }: PayloadAction<Split | InitialSplit>,
    ): void => {
        if (state.splittingState) {
            if ('switchId' in payload) {
                state.splittingState.splits = state.splittingState.splits
                    .filter((split) => split.switchId !== payload.switchId)
                    .concat([payload]);
            } else {
                state.splittingState.initialSplit = payload;
            }
        }
    },
};