import { connect, Provider } from 'react-redux';
import { dataProductsStore } from 'store/store';
import React from 'react';
import { PersistGate } from 'redux-persist/integration/react';
import { persistStore } from 'redux-persist';
import ElementListView from 'data-products/element-list/element-list-view';

const persistorDp = persistStore(dataProductsStore);

const DataProductsMainContainer = connect()(ElementListView);

export const DataProductsMainContainerWithProvider: React.FC = () => {
    return (
        <Provider store={dataProductsStore}>
            <PersistGate loading={null} persistor={persistorDp}>
                <DataProductsMainContainer />
            </PersistGate>
        </Provider>
    );
};
