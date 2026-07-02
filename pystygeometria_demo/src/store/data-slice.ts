import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { apiGet, ApiConfig } from "../api/client";
import {
  CommonData,
  ExtLocationTrack,
  ExtLocationTrackCollectionResponse,
  ExtLocationTrackGeometryResponse,
  ExtLocationTrackProfileResponse,
  ExtOperationalPoint,
  ExtOperationalPointCollectionResponse,
  ExtTrackNumber,
  ExtTrackNumberCollectionResponse,
  LocationTrackResponse,
} from "../api/types";
import { apiConfigSet } from "./config-slice";

export interface AsyncData<T> {
  status: "idle" | "loading" | "ready" | "error";
  data?: T;
  error?: string;
}

interface DataState {
  commonData: AsyncData<{
    trackNumbers: ExtTrackNumber[];
    operationalPoints: ExtOperationalPoint[];
  }>;
  trackNumberTracks: AsyncData<ExtLocationTrack[]> & {
    trackNumberOid?: string;
  };
  locationTracks: Record<string, AsyncData<LocationTrackResponse>>;
}

const initialState: DataState = {
  commonData: { status: "idle" },
  trackNumberTracks: { status: "idle" },
  locationTracks: {},
};

interface ThunkApiConfig {
  state: { config: ApiConfig };
}

export const fetchCommonData = createAsyncThunk<
  CommonData,
  void,
  ThunkApiConfig
>("data/fetchCommonData", async (_, { getState }) => {
  const trackNumbers = apiGet<ExtTrackNumberCollectionResponse>(
    getState().config,
    "/paikannuspohja/v1/ratanumerot",
  );
  const operationalPoints = apiGet<ExtOperationalPointCollectionResponse>(
    getState().config,
    "/paikannuspohja/v1/toiminnalliset-pisteet",
  );
  return Promise.all([trackNumbers, operationalPoints]).then(
    ([trackNumbers, operationalPoints]) => ({
      trackNumbers: trackNumbers.ratanumerot,
      operationalPoints: operationalPoints.toiminnalliset_pisteet,
    }),
  );
});

export const fetchTrackNumberTracks = createAsyncThunk<
  ExtLocationTrack[],
  string,
  ThunkApiConfig
>("data/fetchLocationTracks", async (trackNumberOid, { getState }) => {
  const response = await apiGet<ExtLocationTrackCollectionResponse>(
    getState().config,
    "/paikannuspohja/v1/sijaintiraiteet",
    { ratanumero_oid: trackNumberOid },
  );
  return response.sijaintiraiteet;
});

export const fetchLocationTrack = createAsyncThunk<
  LocationTrackResponse,
  string,
  ThunkApiConfig
>("data/fetchTrack", async (locationTrackOid, { getState }) => {
  const geometry = apiGet<ExtLocationTrackGeometryResponse>(
    getState().config,
    `/paikannuspohja/v1/sijaintiraiteet/${encodeURIComponent(locationTrackOid)}/geometria`,
    { osoitepistevali: "10" },
  );
  const profile = apiGet<ExtLocationTrackProfileResponse>(
    getState().config,
    `/paikannuspohja/v1/sijaintiraiteet/${encodeURIComponent(locationTrackOid)}/pystygeometria`,
  );
  return Promise.all([profile, geometry]).then(([profile, geometry]) => ({
    profile,
    geometry,
  }));
});

export const dataSlice = createSlice({
  name: "data",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(apiConfigSet, () => initialState)
      .addCase(fetchCommonData.pending, (state) => {
        state.commonData = { status: "loading" };
      })
      .addCase(fetchCommonData.fulfilled, (state, action) => {
        state.commonData = { status: "ready", data: action.payload };
      })
      .addCase(fetchCommonData.rejected, (state, action) => {
        state.commonData = { status: "error", error: action.error.message };
      })
      .addCase(fetchTrackNumberTracks.pending, (state, action) => {
        state.trackNumberTracks = {
          status: "loading",
          trackNumberOid: action.meta.arg,
        };
      })
      .addCase(fetchTrackNumberTracks.fulfilled, (state, action) => {
        if (state.trackNumberTracks.trackNumberOid === action.meta.arg) {
          state.trackNumberTracks = {
            status: "ready",
            data: action.payload,
            trackNumberOid: action.meta.arg,
          };
        }
      })
      .addCase(fetchTrackNumberTracks.rejected, (state, action) => {
        if (state.trackNumberTracks.trackNumberOid === action.meta.arg) {
          state.trackNumberTracks = {
            status: "error",
            error: action.error.message,
            trackNumberOid: action.meta.arg,
          };
        }
      })
      .addCase(fetchLocationTrack.pending, (state, action) => {
        state.locationTracks[action.meta.arg] = { status: "loading" };
      })
      .addCase(fetchLocationTrack.fulfilled, (state, action) => {
        state.locationTracks[action.meta.arg] = {
          status: "ready",
          data: action.payload,
        };
      })
      .addCase(fetchLocationTrack.rejected, (state, action) => {
        state.locationTracks[action.meta.arg] = {
          status: "error",
          error: action.error.message,
        };
      });
  },
});
