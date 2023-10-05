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

const trackLayoutAppLevelReducer: typeof trackLayoutReducer = (state, action) => {
    return trackLayoutReducer(action.type === RESET_STORE_ACTION.type ? undefined : state, action);
};

const persistedTrackLayoutReducer = persistReducer(
    trackLayoutPersistConfig,
    trackLayoutAppLevelReducer,
);

const infraModelPersistConfig = {
    key: 'rootInfraModel',
    storage,
};
const infraModelAppLevelReducer: typeof infraModelReducer = (state, action) => {
    return infraModelReducer(action.type === RESET_STORE_ACTION.type ? undefined : state, action);
};
const persistedInfraModelReducer = persistReducer(
    infraModelPersistConfig,
    infraModelAppLevelReducer,
);

const dataProductsPersistConfig = {
    key: 'rootDataProducts',
    storage,
};
const dataProductsAppLevelReducer: typeof dataProductsReducer = (state, action) => {
    return dataProductsReducer(action.type === RESET_STORE_ACTION.type ? undefined : state, action);
};

const persistedDataProductsReducer = persistReducer(
    dataProductsPersistConfig,
    dataProductsAppLevelReducer,
);

const commonPersistConfig = {
    key: 'common',
    storage,
};
const commonAppLevelReducer: typeof commonReducer = (state, action) => {
    return commonReducer(action.type === RESET_STORE_ACTION.type ? undefined : state, action);
};

const persistedCommonReducer = persistReducer(commonPersistConfig, commonAppLevelReducer);

export const appStore = configureStore({
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

export type AppState = ReturnType<typeof appStore.getState>;
export type AppDispatch = typeof appStore.dispatch;
