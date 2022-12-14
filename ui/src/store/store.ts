import { configureStore } from '@reduxjs/toolkit';
import { trackLayoutReducer } from 'track-layout/track-layout-store';
import { infraModelReducer } from 'infra-model/infra-model-store';
import storage from 'redux-persist/lib/storage';
import { combineReducers } from 'redux';
import { persistReducer } from 'redux-persist';
import thunk from 'redux-thunk';

export const RESET_STORE_ACTION = {
    type: 'RESET_STORE',
};

const trackLayoutReducers = combineReducers({
    trackLayout: trackLayoutReducer,
});

const inframodelReducers = combineReducers({
    infraModel: infraModelReducer,
});

// Wrap combined reducers to handle root level state
const trackLayoutRootReducer: typeof trackLayoutReducers = (state, action) => {
    if (action.type == RESET_STORE_ACTION.type) {
        // Reset to initial state
        return trackLayoutReducers(undefined, action);
    }
    return trackLayoutReducers(state, action);
};

const inframodelRootReducer: typeof inframodelReducers = (state, action) => {
    if (action.type == RESET_STORE_ACTION.type) {
        // Reset to initial state
        return inframodelReducers(undefined, action);
    }
    return inframodelReducers(state, action);
};

const trackLayoutPersistConfig = {
    key: 'rootTracklayout',
    storage,
};

const inframodelPersistConfig = {
    key: 'rootInfraModel',
    storage,
};

const persistedTrackLayoutReducer = persistReducer(
    trackLayoutPersistConfig,
    trackLayoutRootReducer,
);
const persistedInframodelReducer = persistReducer(inframodelPersistConfig, inframodelRootReducer);

export const trackLayoutStore = configureStore({
    reducer: persistedTrackLayoutReducer,
    middleware: (getDefaultMiddleware) => [
        ...getDefaultMiddleware({
            // For now we don't need to store our redux store and therefore
            // we don't need to ensure that everything in the store is serializable.
            serializableCheck: false,
        }),
        thunk,
    ],
});

export const inframodelStore = configureStore({
    reducer: persistedInframodelReducer,
    middleware: (getDefaultMiddleware) => [
        ...getDefaultMiddleware({
            // For now we don't need to store our redux store and therefore
            // we don't need to ensure that everything in the store is serializable.
            serializableCheck: false,
        }),
        thunk,
    ],
});

export type TrackLayoutRootState = ReturnType<typeof trackLayoutStore.getState>;
export type InfraModelRootState = ReturnType<typeof inframodelStore.getState>;
export type TrackLayoutAppDispatch = typeof trackLayoutStore.dispatch;
export type InframodelDispatch = typeof inframodelStore.dispatch;
