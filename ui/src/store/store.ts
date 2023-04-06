import { configureStore } from '@reduxjs/toolkit';
import { trackLayoutReducer } from 'track-layout/track-layout-slice';
import { infraModelReducer } from 'infra-model/infra-model-slice';
import { dataProductsReducer } from 'data-products/data-products-slice';
import { persistReducer } from 'redux-persist';
import storage from 'redux-persist/lib/storage';
import { combineReducers } from 'redux';

import thunk from 'redux-thunk';
import { commonReducer } from 'common/common-slice';

export const RESET_STORE_ACTION = {
    type: 'RESET_STORE',
};

const trackLayoutPersistConfig = {
    key: 'rootTracklayout',
    storage,
};
// Wrap combined reducers to handle root level state
const trackLayoutRootReducer: typeof trackLayoutReducer = (state, action) => {
    if (action.type == RESET_STORE_ACTION.type) {
        // Reset to initial state
        return trackLayoutReducer(undefined, action);
    }
    return trackLayoutReducer(state, action);
};

const persistedTrackLayoutReducer = persistReducer(
    trackLayoutPersistConfig,
    trackLayoutRootReducer,
);

const infraModelPersistConfig = {
    key: 'rootInfraModel',
    storage,
};
const infraModelRootReducer: typeof infraModelReducer = (state, action) => {
    if (action.type == RESET_STORE_ACTION.type) {
        // Reset to initial state
        return infraModelReducer(undefined, action);
    }
    return infraModelReducer(state, action);
};
const persistedInfraModelReducer = persistReducer(infraModelPersistConfig, infraModelRootReducer);

const dataProductsPersistConfig = {
    key: 'rootDataProducts',
    storage,
};
const dataProductsRootReducer: typeof dataProductsReducer = (state, action) => {
    if (action.type == RESET_STORE_ACTION.type) {
        // Reset to initial state
        return dataProductsReducer(undefined, action);
    }
    return dataProductsReducer(state, action);
};

const persistedDataProductsReducer = persistReducer(
    dataProductsPersistConfig,
    dataProductsRootReducer,
);

const commonPersistConfig = {
    key: 'common',
    storage,
};
const commonDataReducer: typeof commonReducer = (state, action) => {
    if (action.type == RESET_STORE_ACTION.type) {
        // Reset to initial state
        return commonReducer(undefined, action);
    }
    return commonReducer(state, action);
};

const persistedCommonReducer = persistReducer(commonPersistConfig, commonDataReducer);

export const rootStore = configureStore({
    reducer: combineReducers({
        trackLayout: persistedTrackLayoutReducer,
        dataProducts: persistedDataProductsReducer,
        infraModel: persistedInfraModelReducer,
        common: persistedCommonReducer,
    }),
    middleware: (getDefaultMiddleware) => [
        ...getDefaultMiddleware({
            // For now we don't need to store our redux store and therefore
            // we don't need to ensure that everything in the store is serializable.
            serializableCheck: false,
        }),
        thunk,
    ],
});

export type RootState = ReturnType<typeof rootStore.getState>;
export type RootDispatch = typeof rootStore.dispatch;
