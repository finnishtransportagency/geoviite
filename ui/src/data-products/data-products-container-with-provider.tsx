import { connect, Provider } from 'react-redux';
import { dataProductsStore } from 'store/store';
import React from 'react';
import { PersistGate } from 'redux-persist/integration/react';
import { persistStore } from 'redux-persist';
import ElementListView from 'data-products/element-list/element-list-view';
import VerticalGeometryView from 'data-products/vertical-geometry/vertical-geometry-view';

const persistorDp = persistStore(dataProductsStore);

type SelectedDataProduct = 'ELEMENT_LIST' | 'VERTICAL_GEOMETRY';

export type DataProductsContainerWithProviderProps = {
    selectedDataProduct: SelectedDataProduct;
};

const DataProductsMainContainerElementListView = connect()(ElementListView);
const DataProductsMainContainerVerticalGeometryView = connect()(VerticalGeometryView);

function renderPath(products: SelectedDataProduct) {
    switch (products) {
        case 'ELEMENT_LIST':
            return (
                <Provider store={dataProductsStore}>
                    <PersistGate loading={null} persistor={persistorDp}>
                        <DataProductsMainContainerElementListView />
                    </PersistGate>
                </Provider>
            );
        case 'VERTICAL_GEOMETRY':
            return (
                <Provider store={dataProductsStore}>
                    <PersistGate loading={null} persistor={persistorDp}>
                        <DataProductsMainContainerVerticalGeometryView />
                    </PersistGate>
                </Provider>
            );
    }
}

export const DataProductsMainContainerWithProvider: React.FC<
    DataProductsContainerWithProviderProps
> = ({ selectedDataProduct }) => {
    return renderPath(selectedDataProduct);
};
