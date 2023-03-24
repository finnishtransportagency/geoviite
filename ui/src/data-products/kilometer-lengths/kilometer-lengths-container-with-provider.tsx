import { connect, Provider } from 'react-redux';
import { dataProductsStore } from 'store/store';
import React from 'react';
import { PersistGate } from 'redux-persist/integration/react';
import { persistStore } from 'redux-persist';
import { KilometerLengthsView } from 'data-products/kilometer-lengths/kilometer-lengths-view';

const persistorDp = persistStore(dataProductsStore);

const DataProductsMainContainer = connect()(KilometerLengthsView);

export const KilometerLengthsContainerWithProvider: React.FC = () => {
    return (
        <Provider store={dataProductsStore}>
            <PersistGate loading={null} persistor={persistorDp}>
                <DataProductsMainContainer />
            </PersistGate>
        </Provider>
    );
};
