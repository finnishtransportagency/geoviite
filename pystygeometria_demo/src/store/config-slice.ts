import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { ApiConfig } from "../api/client";

const initialState: ApiConfig = {
  environment: "local",
  devApiKey: "",
  prodApiKey: "",
};

export const configSlice = createSlice({
  name: "config",
  initialState,
  reducers: {
    apiConfigSet: (_state, action: PayloadAction<ApiConfig>) => action.payload,
  },
});

export const { apiConfigSet } = configSlice.actions;
