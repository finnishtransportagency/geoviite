import { configureStore } from '@reduxjs/toolkit';
import { trackLayoutReducer } from 'track-layout/track-layout-slice';
import { infraModelReducer } from 'infra-model/infra-model-slice';
import { dataProductsReducer } from 'data-products/data-products-slice';
import { FLUSH, PAUSE, PERSIST, PURGE, REGISTER, REHYDRATE, persistReducer } from 'redux-persist';
import storage from 'redux-persist/lib/storage';
import { combineReducers } from 'redux';

import { commonReducer } from 'common/common-slice';

const persistedTrackLayoutReducer = persistReducer(
    {
        key: 'rootTracklayout',
        storage,
    },
    trackLayoutReducer,
);

const persistedInfraModelReducer = persistReducer(
    {
        key: 'rootInfraModel',
        storage,
    },
    infraModelReducer,
);

const persistedDataProductsReducer = persistReducer(
    {
        key: 'rootDataProducts',
        storage,
    },
    dataProductsReducer,
);

const persistedCommonReducer = persistReducer(
    {
        key: 'common',
        storage,
    },
    commonReducer,
);

export const appStore = configureStore({
    reducer: combineReducers({
        trackLayout: persistedTrackLayoutReducer,
        dataProducts: persistedDataProductsReducer,
        infraModel: persistedInfraModelReducer,
        common: persistedCommonReducer,
    }),
    middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware({
            serializableCheck: {
                ignoredActions: [FLUSH, REHYDRATE, PAUSE, PERSIST, PURGE, REGISTER],
            },
            thunk: {
                extraArgument: undefined,
            },
        }),
});

export type AppState = ReturnType<typeof appStore.getState>;
export type AppDispatch = typeof appStore.dispatch;
