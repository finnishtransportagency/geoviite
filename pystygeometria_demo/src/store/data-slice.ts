import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { apiGet, apiGetAllowingNoContent, ApiConfig } from "../api/client";
import {
  CommonData,
  ExtLocationTrack,
  ExtLocationTrackCollectionResponse,
  ExtLocationTrackGeometryResponse,
  ExtLocationTrackProfileResponse,
  ExtLocationTrackResponse,
  ExtOperationalPoint,
  ExtOperationalPointCollectionResponse,
  ExtRouteResponse,
  ExtTrackNumber,
  ExtTrackNumberCollectionResponse,
  LocationTrackResponse,
} from "../api/types";
import { apiConfigSet } from "./config-slice";
import { routeStartSet, routeEndSet } from "./selection-slice";

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
  // The latest requested route; data is null when the API found no route between the
  // given locations (204). `key` identifies the request (see routeRequestKey) so a
  // stale response cannot overwrite a newer request's state.
  route: AsyncData<ExtRouteResponse | null> & { key?: string };
  locationTracks: Record<string, AsyncData<LocationTrackResponse>>;
}

const initialState: DataState = {
  commonData: { status: "idle" },
  trackNumberTracks: { status: "idle" },
  route: { status: "idle" },
  locationTracks: {},
};

interface ThunkApiConfig {
  state: { config: ApiConfig; data: DataState };
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
  const info = apiGet<ExtLocationTrackResponse>(
    getState().config,
    `/paikannuspohja/v1/sijaintiraiteet/${encodeURIComponent(locationTrackOid)}`,
  );
  const geometry = apiGet<ExtLocationTrackGeometryResponse>(
    getState().config,
    `/paikannuspohja/v1/sijaintiraiteet/${encodeURIComponent(locationTrackOid)}/geometria`,
    { osoitepistevali: "10" },
  );
  const profile = apiGet<ExtLocationTrackProfileResponse>(
    getState().config,
    `/paikannuspohja/v1/sijaintiraiteet/${encodeURIComponent(locationTrackOid)}/pystygeometria`,
  );
  return Promise.all([info, profile, geometry]).then(
    ([info, profile, geometry]) => ({
      info: info.sijaintiraide,
      profile,
      geometry,
    }),
  );
});

export interface RouteRequest {
  start: { x: number; y: number };
  end: { x: number; y: number };
}

export function routeRequestKey(request: RouteRequest): string {
  return `${request.start.x},${request.start.y}->${request.end.x},${request.end.y}`;
}

// Fetches the route between two map locations and kicks off loading the profile and
// geometry of every location track the route runs over. Resolves to null when the API
// reports that no route exists (204 No Content).
export const fetchRoute = createAsyncThunk<
  ExtRouteResponse | null,
  RouteRequest,
  ThunkApiConfig
>("data/fetchRoute", async (request, { getState, dispatch }) => {
  const response = await apiGetAllowingNoContent<ExtRouteResponse>(
    getState().config,
    "/paikannuspohja/v1/reititys",
    {
      sijainti_alku_x: String(request.start.x),
      sijainti_alku_y: String(request.start.y),
      sijainti_loppu_x: String(request.end.x),
      sijainti_loppu_y: String(request.end.y),
    },
  );
  if (!response) {
    return null;
  }
  const trackOids = new Set(
    response.reitti.reitin_osat.map((section) => section.sijaintiraide_oid),
  );
  for (const oid of trackOids) {
    const existing = getState().data.locationTracks[oid];
    if (!existing || existing.status === "error") {
      dispatch(fetchLocationTrack(oid));
    }
  }
  return response;
});

export const dataSlice = createSlice({
  name: "data",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(apiConfigSet, () => initialState)
      // Clearing either route endpoint empties the diagram: without both endpoints
      // there is no route to display, so drop the stale route response.
      .addCase(routeStartSet, (state, action) => {
        if (action.payload === undefined) {
          state.route = { status: "idle" };
        }
      })
      .addCase(routeEndSet, (state, action) => {
        if (action.payload === undefined) {
          state.route = { status: "idle" };
        }
      })
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
      .addCase(fetchRoute.pending, (state, action) => {
        state.route = {
          status: "loading",
          key: routeRequestKey(action.meta.arg),
        };
      })
      .addCase(fetchRoute.fulfilled, (state, action) => {
        if (state.route.key === routeRequestKey(action.meta.arg)) {
          state.route = {
            status: "ready",
            data: action.payload,
            key: state.route.key,
          };
        }
      })
      .addCase(fetchRoute.rejected, (state, action) => {
        if (state.route.key === routeRequestKey(action.meta.arg)) {
          state.route = {
            status: "error",
            error: action.error.message,
            key: state.route.key,
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
