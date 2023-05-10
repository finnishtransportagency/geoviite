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
            if (toDate(changeTimes.layoutTrackNumber) < toDate(payload.layoutTrackNumber)) {
                changeTimes.layoutTrackNumber = payload.layoutTrackNumber;
            }
            if (toDate(changeTimes.layoutLocationTrack) < toDate(payload.layoutLocationTrack)) {
                changeTimes.layoutLocationTrack = payload.layoutLocationTrack;
            }
            if (toDate(changeTimes.layoutReferenceLine) < toDate(payload.layoutReferenceLine)) {
                changeTimes.layoutReferenceLine = payload.layoutReferenceLine;
            }
            if (toDate(changeTimes.layoutSwitch) < toDate(payload.layoutSwitch)) {
                changeTimes.layoutSwitch = payload.layoutSwitch;
            }
            if (toDate(changeTimes.layoutKmPost) < toDate(payload.layoutKmPost)) {
                changeTimes.layoutKmPost = payload.layoutKmPost;
            }
            if (toDate(changeTimes.geometryPlan) < toDate(payload.geometryPlan)) {
                changeTimes.geometryPlan = payload.geometryPlan;
            }
            if (toDate(changeTimes.publication) < toDate(payload.publication)) {
                changeTimes.publication = payload.publication;
            }
        },
        setLayoutTrackNumberChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.layoutTrackNumber) < toDate(payload))
                changeTimes.layoutTrackNumber = payload;
        },
        setLayoutLocationTrackChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.layoutLocationTrack) < toDate(payload))
                changeTimes.layoutLocationTrack = payload;
        },
        setLayoutReferenceLineChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.layoutReferenceLine) < toDate(payload))
                changeTimes.layoutReferenceLine = payload;
        },
        setLayoutSwitchChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.layoutSwitch) < toDate(payload))
                changeTimes.layoutSwitch = payload;
        },
        setLayoutKmPostChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.layoutKmPost) < toDate(payload))
                changeTimes.layoutKmPost = payload;
        },
        setGeometryPlanChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.geometryPlan) < toDate(payload))
                changeTimes.geometryPlan = payload;
        },
        setPublicationChangeTime: function (
            { changeTimes }: CommonState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.publication) < toDate(payload))
                changeTimes.publication = payload;
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
