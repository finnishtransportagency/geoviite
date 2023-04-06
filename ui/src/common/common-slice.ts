import { createSlice, PayloadAction } from '@reduxjs/toolkit';

export type CommonState = {
    version: string | undefined;
};

export const initialCommonState = {
    version: undefined,
};

const commonSlice = createSlice({
    name: 'common',
    initialState: initialCommonState,
    reducers: {
        setVersion: (state: CommonState, { payload: version }: PayloadAction<string>): void => {
            state.version = version;
        },
    },
});

export const commonReducer = commonSlice.reducer;
export const commonActionCreators = commonSlice.actions;
