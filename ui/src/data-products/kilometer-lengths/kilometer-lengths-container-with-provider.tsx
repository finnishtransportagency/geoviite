import { connect } from 'react-redux'; //, Provider
//import { trackLayoutStore } from 'store/store';
import React from 'react';
//import { PersistGate } from 'redux-persist/integration/react';
//import { persistStore } from 'redux-persist';
import { KilometerLengthsView } from 'data-products/kilometer-lengths/kilometer-lengths-view';

//const persistorDp = persistStore(trackLayoutStore);

const DataProductsMainContainer = connect()(KilometerLengthsView);

export const KilometerLengthsContainerWithProvider: React.FC = () => {
    return <DataProductsMainContainer />;
};

/*
<Provider store={trackLayoutStore}>
            <PersistGate loading={null} persistor={persistorDp}>
                <DataProductsMainContainer />
            </PersistGate>
        </Provider>
 */
