import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { apiConfigSet } from "./config-slice";
import { LocationTrackType } from "../api/types";

interface SelectionState {
  trackNumberOid?: string;
  addressStart: string;
  addressEnd: string;
  selectedLocationTrackOids: string[];
  trackTypeFilter: LocationTrackType[];
}

const initialState: SelectionState = {
  addressStart: "",
  addressEnd: "",
  selectedLocationTrackOids: [],
  trackTypeFilter: ["pääraide"],
};

export const selectionSlice = createSlice({
  name: "selection",
  initialState,
  reducers: {
    trackNumberSelected: (
      state,
      action: PayloadAction<{
        oid: string;
        addressStart: string;
        addressEnd: string;
      }>,
    ) => ({
      trackNumberOid: action.payload.oid,
      addressStart: action.payload.addressStart,
      addressEnd: action.payload.addressEnd,
      selectedLocationTrackOids: [],
      trackTypeFilter: state.trackTypeFilter,
    }),
    addressStartSet: (state, action: PayloadAction<string>) => {
      state.addressStart = action.payload;
    },
    addressEndSet: (state, action: PayloadAction<string>) => {
      state.addressEnd = action.payload;
    },
    trackTypeFilterToggled: (
      state,
      action: PayloadAction<LocationTrackType>,
    ) => {
      const type = action.payload;
      if (state.trackTypeFilter.includes(type)) {
        state.trackTypeFilter = state.trackTypeFilter.filter((t) => t !== type);
      } else {
        state.trackTypeFilter.push(type);
      }
    },
    locationTrackToggled: (state, action: PayloadAction<string>) => {
      const oid = action.payload;
      if (state.selectedLocationTrackOids.includes(oid)) {
        state.selectedLocationTrackOids =
          state.selectedLocationTrackOids.filter(
            (selected) => selected !== oid,
          );
      } else {
        state.selectedLocationTrackOids.push(oid);
      }
    },
  },
  extraReducers: (builder) => {
    builder.addCase(apiConfigSet, () => initialState);
  },
});

export const {
  trackNumberSelected,
  addressStartSet,
  addressEndSet,
  trackTypeFilterToggled,
  locationTrackToggled,
} = selectionSlice.actions;
