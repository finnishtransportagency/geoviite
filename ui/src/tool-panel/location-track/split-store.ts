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
    initialSplit: InitialSplit;
    splits: Split[];
};

type SplitStart = {
    locationTrack: LayoutLocationTrack;
    allowedSwitches: SwitchOnLocationTrack[];
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

export const splitReducers = {
    onStartSplitting: (state: TrackLayoutState, { payload }: PayloadAction<SplitStart>): void => {
        state.publishType = 'DRAFT';
        state.splittingState = {
            originLocationTrack: payload.locationTrack,
            allowedSwitches: payload.allowedSwitches,
            splits: [],
            endLocation: payload.endLocation,
            initialSplit: {
                name: '',
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
            state.splittingState.splits = state.splittingState.splits.concat([
                {
                    switchId: payload,
                    name: '',
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
