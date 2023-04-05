import { connect } from 'react-redux'; //, Provider
//import { trackLayoutStore } from 'store/store';
import React from 'react';
//import { PersistGate } from 'redux-persist/integration/react';
//import { persistStore } from 'redux-persist';
import ElementListView from 'data-products/element-list/element-list-view';

//const persistorDp = persistStore(trackLayoutStore);

const DataProductsMainContainer = connect()(ElementListView);

export const ElementListContainerWithProvider: React.FC = () => {
    return <DataProductsMainContainer />;
};
/*
<Provider store={trackLayoutStore}>
    <PersistGate loading={null} persistor={persistorDp}>
        <DataProductsMainContainer />
    </PersistGate>
</Provider>
*/
