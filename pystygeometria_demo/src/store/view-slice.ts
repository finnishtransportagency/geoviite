import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { ViewRange } from "../math/layout";
import { apiConfigSet } from "./config-slice";
import { fetchRoute } from "./data-slice";
import { selectionModeSet, trackNumberSelected } from "./selection-slice";

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
    // Both changing the routing request and switching between the selection modes
    // replace the displayed spans wholesale; show the new full extent.
    builder.addCase(fetchRoute.pending, () => initialState);
    builder.addCase(selectionModeSet, () => initialState);
  },
});

export const { viewRangeSet, viewReset } = viewSlice.actions;
