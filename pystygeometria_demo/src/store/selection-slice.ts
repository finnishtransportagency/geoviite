import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { apiConfigSet } from "./config-slice";
import { LocationTrackType } from "../api/types";

// How the displayed track spans are chosen: by picking a track number and its location
// tracks by hand, or by routing between two operational points.
export type SelectionMode = "trackNumber" | "route";

interface SelectionState {
  mode: SelectionMode;
  trackNumberOid?: string;
  addressStart: string;
  addressEnd: string;
  selectedLocationTrackOids: string[];
  trackTypeFilter: LocationTrackType[];
  // Operational point oids bounding the route in route mode. Kept when switching
  // modes, so flipping back and forth does not lose either selection.
  routeStartOid?: string;
  routeEndOid?: string;
}

const initialState: SelectionState = {
  mode: "trackNumber",
  addressStart: "",
  addressEnd: "",
  selectedLocationTrackOids: [],
  trackTypeFilter: ["pääraide"],
};

export const selectionSlice = createSlice({
  name: "selection",
  initialState,
  reducers: {
    selectionModeSet: (state, action: PayloadAction<SelectionMode>) => {
      state.mode = action.payload;
    },
    trackNumberSelected: (
      state,
      action: PayloadAction<{
        oid: string;
        addressStart: string;
        addressEnd: string;
      }>,
    ) => ({
      ...state,
      trackNumberOid: action.payload.oid,
      addressStart: action.payload.addressStart,
      addressEnd: action.payload.addressEnd,
      selectedLocationTrackOids: [],
    }),
    routeStartSet: (state, action: PayloadAction<string | undefined>) => {
      state.routeStartOid = action.payload;
    },
    routeEndSet: (state, action: PayloadAction<string | undefined>) => {
      state.routeEndOid = action.payload;
    },
    routeEndpointsSwapped: (state) => {
      const start = state.routeStartOid;
      state.routeStartOid = state.routeEndOid;
      state.routeEndOid = start;
    },
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
  selectionModeSet,
  trackNumberSelected,
  addressStartSet,
  addressEndSet,
  trackTypeFilterToggled,
  locationTrackToggled,
  routeStartSet,
  routeEndSet,
  routeEndpointsSwapped,
} = selectionSlice.actions;
