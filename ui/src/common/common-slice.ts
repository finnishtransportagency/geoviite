import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { TimeStamp } from 'common/common-model';
import { toDate } from 'utils/date-utils';

export type ChangeTimes = {
    layoutTrackNumber: TimeStamp;
    layoutLocationTrack: TimeStamp;
    layoutReferenceLine: TimeStamp;
    layoutSwitch: TimeStamp;
    layoutKmPost: TimeStamp;
    geometryPlan: TimeStamp;
    publication: TimeStamp;
    velhoDocument: TimeStamp;
};

export const initialChangeTime: TimeStamp = '1970-01-01T00:00:00.000Z';
export const initialChangeTimes: ChangeTimes = {
    layoutTrackNumber: initialChangeTime,
    layoutLocationTrack: initialChangeTime,
    layoutReferenceLine: initialChangeTime,
    layoutSwitch: initialChangeTime,
    layoutKmPost: initialChangeTime,
    geometryPlan: initialChangeTime,
    publication: initialChangeTime,
    velhoDocument: initialChangeTime,
};

export type CommonState = {
    version: string | undefined;
    changeTimes: ChangeTimes;
    userHasWriteRole: boolean;
};

export const initialCommonState = {
    version: undefined,
    changeTimes: initialChangeTimes,
    userHasWriteRole: false,
};

const updateChangeTime = (changeTimes: ChangeTimes, key: keyof ChangeTimes, time: TimeStamp) => {
    if (toDate(changeTimes[key]) < toDate(time)) changeTimes[key] = time;
};

const commonSlice = createSlice({
    name: 'common',
    initialState: initialCommonState,
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
        setPublicationChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            updateChangeTime(changeTimes, 'publication', payload);
        },
        setVelhoDocumentChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            updateChangeTime(changeTimes, 'velhoDocument', payload);
        },
        setUserHasWriteRole: (
            state: CommonState,
            { payload: writeRole }: PayloadAction<boolean>,
        ): void => {
            state.userHasWriteRole = writeRole;
        },
    },
});

export const commonReducer = commonSlice.reducer;
export const commonActionCreators = commonSlice.actions;
