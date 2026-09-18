import { combineReducers, configureStore } from "@reduxjs/toolkit";
import { TypedUseSelectorHook, useDispatch, useSelector } from "react-redux";
import {
  FLUSH,
  PAUSE,
  PERSIST,
  persistReducer,
  persistStore,
  PURGE,
  REGISTER,
  REHYDRATE,
} from "redux-persist";
import storage from "redux-persist/lib/storage";
import { configSlice } from "./config-slice";
import { dataSlice } from "./data-slice";
import { selectionSlice } from "./selection-slice";
import { viewSlice } from "./view-slice";

const configPersistConfig = {
  key: "config",
  storage,
  whitelist: ["environment", "devApiKey", "prodApiKey"],
};

const rootReducer = combineReducers({
  config: persistReducer(configPersistConfig, configSlice.reducer),
  data: dataSlice.reducer,
  selection: selectionSlice.reducer,
  view: viewSlice.reducer,
});

export const store = configureStore({
  reducer: rootReducer,
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredActions: [FLUSH, REHYDRATE, PAUSE, PERSIST, PURGE, REGISTER],
      },
    }),
});

export const persistor = persistStore(store);

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

export const useAppDispatch: () => AppDispatch = useDispatch;
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;
