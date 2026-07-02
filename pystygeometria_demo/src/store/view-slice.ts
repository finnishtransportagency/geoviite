import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { ViewRange } from "../math/layout";
import { apiConfigSet } from "./config-slice";
import { trackNumberSelected } from "./selection-slice";

interface ViewState {
  // null means "show the full extent of the displayed tracks"
  range: ViewRange | null;
}

const initialState: ViewState = { range: null };

export const viewSlice = createSlice({
  name: "view",
  initialState,
  reducers: {
    viewRangeSet: (state, action: PayloadAction<ViewRange>) => {
      state.range = action.payload;
    },
    viewReset: () => initialState,
  },
  extraReducers: (builder) => {
    builder.addCase(apiConfigSet, () => initialState);
    builder.addCase(trackNumberSelected, () => initialState);
  },
});

export const { viewRangeSet, viewReset } = viewSlice.actions;
