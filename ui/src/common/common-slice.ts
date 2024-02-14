import { ActionReducerMapBuilder, createSlice, PayloadAction } from '@reduxjs/toolkit';
import { TimeStamp } from 'common/common-model';
import { toDate } from 'utils/date-utils';
import { Privilege } from 'user/user-model';
import { PURGE } from 'redux-persist';

export type ChangeTimes = {
    layoutTrackNumber: TimeStamp;
    layoutLocationTrack: TimeStamp;
    layoutReferenceLine: TimeStamp;
    layoutSwitch: TimeStamp;
    layoutKmPost: TimeStamp;
    geometryPlan: TimeStamp;
    project: TimeStamp;
    author: TimeStamp;
    publication: TimeStamp;
    ratkoPush: TimeStamp;
    pvDocument: TimeStamp;
    split: TimeStamp;
};

export const initialChangeTime: TimeStamp = '1970-01-01T00:00:00.000Z';
export const initialChangeTimes: ChangeTimes = {
    layoutTrackNumber: initialChangeTime,
    layoutLocationTrack: initialChangeTime,
    layoutReferenceLine: initialChangeTime,
    layoutSwitch: initialChangeTime,
    layoutKmPost: initialChangeTime,
    geometryPlan: initialChangeTime,
    project: initialChangeTime,
    author: initialChangeTime,
    publication: initialChangeTime,
    ratkoPush: initialChangeTime,
    pvDocument: initialChangeTime,
    split: initialChangeTime,
};

export type CommonState = {
    version: string | undefined;
    changeTimes: ChangeTimes;
    userPrivileges: Privilege[];
};

export const initialCommonState = {
    version: undefined,
    changeTimes: initialChangeTimes,
    userPrivileges: [],
};

const updateChangeTime = (changeTimes: ChangeTimes, key: keyof ChangeTimes, time: TimeStamp) => {
    if (toDate(changeTimes[key]) < toDate(time)) changeTimes[key] = time;
};

const commonSlice = createSlice({
    name: 'common',
    initialState: initialCommonState,
    extraReducers: (builder: ActionReducerMapBuilder<CommonState>) => {
        builder.addCase(PURGE, (_state, _action) => {
            return initialCommonState;
        });
    },
    reducers: {
        setVersion: (state: CommonState, { payload: version }: PayloadAction<string>): void => {
            state.version = version;
        },
        setChangeTimes: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<ChangeTimes>,
        ) {
            Object.keys(payload).forEach((key: keyof ChangeTimes) => {
                updateChangeTime(changeTimes, key, payload[key]);
            });
        },
        setLayoutTrackNumberChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            updateChangeTime(changeTimes, 'layoutTrackNumber', payload);
        },
        setLayoutLocationTrackChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            updateChangeTime(changeTimes, 'layoutLocationTrack', payload);
        },
        setLayoutReferenceLineChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            updateChangeTime(changeTimes, 'layoutReferenceLine', payload);
        },
        setLayoutSwitchChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            updateChangeTime(changeTimes, 'layoutSwitch', payload);
        },
        setLayoutKmPostChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            updateChangeTime(changeTimes, 'layoutKmPost', payload);
        },
        setGeometryPlanChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            updateChangeTime(changeTimes, 'geometryPlan', payload);
        },
        setProjectChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            updateChangeTime(changeTimes, 'project', payload);
        },
        setPublicationChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            updateChangeTime(changeTimes, 'publication', payload);
        },
        setPVDocumentChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            updateChangeTime(changeTimes, 'pvDocument', payload);
        },
        setRatkoPushChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.ratkoPush) < toDate(payload)) changeTimes.ratkoPush = payload;
        },
        setSplitChangeTime: function ({ changeTimes }, { payload }) {
            updateChangeTime(changeTimes, 'split', payload);
        },
        setUserPrivileges: (
            state: CommonState,
            { payload: privileges }: PayloadAction<Privilege[]>,
        ): void => {
            state.userPrivileges = privileges;
        },
    },
});

export const commonReducer = commonSlice.reducer;
export const commonActionCreators = commonSlice.actions;
