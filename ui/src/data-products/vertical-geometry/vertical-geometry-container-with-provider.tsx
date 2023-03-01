import { connect, Provider } from 'react-redux';
import { dataProductsStore } from 'store/store';
import React from 'react';
import { PersistGate } from 'redux-persist/integration/react';
import { persistStore } from 'redux-persist';
import VerticalGeometryView from 'data-products/vertical-geometry/vertical-geometry-view';

const persistorDp = persistStore(dataProductsStore);

const DataProductsMainContainer = connect()(VerticalGeometryView);

export const VerticalGeometryContainerWithProvider: React.FC = () => {
    return (
        <Provider store={dataProductsStore}>
            <PersistGate loading={null} persistor={persistorDp}>
                <DataProductsMainContainer />
            </PersistGate>
        </Provider>
    );
};
