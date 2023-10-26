import { PayloadAction } from '@reduxjs/toolkit';
import {
    LayoutLocationTrack,
    LayoutSwitchId,
    LocationTrackDescriptionSuffixMode,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { TrackLayoutState } from 'track-layout/track-layout-slice';
import { compareTrackMeterStrings, TrackMeter } from 'common/common-model';
import { formatTrackMeter } from 'utils/geography-utils';

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
            const newSplits = (state.splittingState.splits || []).concat([
                {
                    switchId: payload,
                    name: '',
                    descriptionBase: '',
                    suffixMode: 'NONE',
                },
            ]);
            newSplits.sort((a, b) => {
                const aAddress = state.splittingState?.allowedSwitches?.find(
                    (sw) => sw.switchId === a.switchId,
                )?.address;
                const bAddress = state.splittingState?.allowedSwitches?.find(
                    (sw) => sw.switchId === b.switchId,
                )?.address;
                if (aAddress && bAddress) {
                    return compareTrackMeterStrings(
                        formatTrackMeter(aAddress),
                        formatTrackMeter(bAddress),
                    );
                } else if (aAddress) return 1;
                else if (bAddress) return -1;
                else return 0;
            });
            state.splittingState.splits = newSplits;
        }
    },
    removeSplit: (state: TrackLayoutState, { payload }: PayloadAction<LayoutSwitchId>): void => {
        if (state.splittingState) {
            state.splittingState.splits = (state.splittingState.splits || []).filter(
                (split) => split.switchId !== payload,
            );
        }
    },
    setSplitDuplicate: (
        state: TrackLayoutState,
        { payload }: PayloadAction<SplitDuplicate>,
    ): void => {
        if (state.splittingState) {
            const split =
                state.splittingState.splits.find((split) => split.switchId === payload.splitId) ??
                state.splittingState.initialSplit;
            if (split) {
                split.duplicateOf = payload.duplicateOf;
            }
        }
    },
};
