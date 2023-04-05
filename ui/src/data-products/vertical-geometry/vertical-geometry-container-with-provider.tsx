import { connect } from 'react-redux'; //, Provider
//import { trackLayoutStore } from 'store/store';
import React from 'react';
//import { PersistGate } from 'redux-persist/integration/react';
//import { persistStore } from 'redux-persist';
import VerticalGeometryView from 'data-products/vertical-geometry/vertical-geometry-view';

//const persistorDp = persistStore(trackLayoutStore);

const DataProductsMainContainer = connect()(VerticalGeometryView);

export const VerticalGeometryContainerWithProvider: React.FC = () => {
    return <DataProductsMainContainer />;
};

/*
<Provider store={trackLayoutStore}>
            <PersistGate loading={null} persistor={persistorDp}>
                <DataProductsMainContainer />
            </PersistGate>
        </Provider>
 */
