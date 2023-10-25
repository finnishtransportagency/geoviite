import { PayloadAction } from '@reduxjs/toolkit';
import {
    LayoutLocationTrack,
    LayoutSwitchId,
    LocationTrackDescriptionSuffixMode,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { TrackLayoutState } from 'track-layout/track-layout-slice';

export type LocationTrackBoundaryEndpoint = {
    type: 'LOCATION_TRACK_START' | 'LOCATION_TRACK_END';
};

export type SwitchEndpoint = {
    type: 'SWITCH';
    switchId: LayoutSwitchId;
};

export type Split = {
    start: LocationTrackBoundaryEndpoint | SwitchEndpoint;
    name: string;
    descriptionBase: string;
    suffixMode: LocationTrackDescriptionSuffixMode;
    duplicateOf?: LocationTrackId;
};

export type SplittingState = {
    originLocationTrack: LayoutLocationTrack;
    splits: Split[];
    endpoint: LocationTrackBoundaryEndpoint;
};

export const splitReducers = {
    onStartSplitting: (
        state: TrackLayoutState,
        { payload }: PayloadAction<LayoutLocationTrack>,
    ): void => {
        state.splittingState = {
            originLocationTrack: payload,
            splits: [
                {
                    start: { type: 'LOCATION_TRACK_START' },
                    name: '',
                    descriptionBase: '',
                    suffixMode: 'NONE',
                },
            ],
            endpoint: { type: 'LOCATION_TRACK_END' },
        };
    },
    cancelSplitting: (state: TrackLayoutState): void => {
        state.splittingState = undefined;
    },
    addSplit: (state: TrackLayoutState, { payload }: PayloadAction<LayoutSwitchId>): void => {
        if (state.splittingState) {
            const splits = (state.splittingState.splits || []).concat([
                {
                    start: { type: 'SWITCH', switchId: payload },
                    name: '',
                    descriptionBase: '',
                    suffixMode: 'NONE',
                },
            ]);
            state.splittingState.splits = splits;
        }
    },
    removeSplit: (state: TrackLayoutState, { payload }: PayloadAction<LayoutSwitchId>): void => {
        if (state.splittingState) {
            state.splittingState.splits = (state.splittingState.splits || []).filter(
                (split) => split.start.type === 'SWITCH' && split.start.switchId === payload,
            );
        }
    },
    setSplitDuplicate: (
        state: TrackLayoutState,
        { payload }: PayloadAction<{ splitSwitchId: LayoutSwitchId; duplicateOf: LocationTrackId }>,
    ): void => {
        if (state.splittingState) {
            const split = state.splittingState.splits.find(
                (split) =>
                    split.start.type === 'SWITCH' && split.start.switchId === payload.splitSwitchId,
            );
            if (split) split.duplicateOf = payload.duplicateOf;
        }
    },
};
