import { PayloadAction } from '@reduxjs/toolkit';
import {
    LayoutLocationTrack,
    LayoutSwitchId,
    LocationTrackDescriptionSuffixMode,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { TrackLayoutState } from 'track-layout/track-layout-slice';
import { TrackMeter } from 'common/common-model';

export type InitialSplit = {
    name: string;
    descriptionBase: string;
    suffixMode: LocationTrackDescriptionSuffixMode;
    duplicateOf?: LocationTrackId;
};

export type Split = InitialSplit & {
    switchId: LayoutSwitchId;
};

export type SplittingState = {
    originLocationTrack: LayoutLocationTrack;
    allowedSwitches: SwitchOnLocationTrack[];
    initialSplit: InitialSplit;
    splits: Split[];
};

type SplitStart = {
    locationTrack: LayoutLocationTrack;
    allowedSwitches: SwitchOnLocationTrack[];
};

export type SwitchOnLocationTrack = {
    switchId: LayoutSwitchId;
    address: TrackMeter | undefined;
};

export type SplitDuplicate = {
    splitId: LayoutSwitchId | undefined;
    duplicateOf: LocationTrackId | undefined;
};

export const splitReducers = {
    onStartSplitting: (state: TrackLayoutState, { payload }: PayloadAction<SplitStart>): void => {
        state.splittingState = {
            originLocationTrack: payload.locationTrack,
            allowedSwitches: payload.allowedSwitches,
            splits: [],
            initialSplit: {
                name: '',
                descriptionBase: '',
                suffixMode: 'NONE',
            },
        };
    },
    cancelSplitting: (state: TrackLayoutState): void => {
        state.splittingState = undefined;
    },
    addSplit: (state: TrackLayoutState, { payload }: PayloadAction<LayoutSwitchId>): void => {
        if (
            state.splittingState &&
            state.splittingState.allowedSwitches.some((sw) => sw.switchId == payload) &&
            !state.splittingState.splits.some((split) => split.switchId === payload)
        ) {
            state.splittingState.splits = state.splittingState.splits.concat([
                {
                    switchId: payload,
                    name: '',
                    descriptionBase: '',
                    suffixMode: 'NONE',
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
