/*
import { connect } from 'react-redux'; //import { connect   } from 'react-redux';
import { InfraModelMainView } from 'infra-model/infra-model-main-view';
//import { inframodelStore } from 'store/store';
import React from 'react';
//import { PersistGate } from 'redux-persist/integration/react';
//import { persistStore } from 'redux-persist';
import { useTrackLayoutAppSelector } from 'store/hooks';

//const persistorIm = persistStore(inframodelStore);

//OTA TÄMÄ TASO KOKONAAN POIS
//KORVAA HOOKSEILLA
//valitse infa-model-slice!!!!!
const InfraModelMainContainer = connect()(InfraModelMainView);

export const InfraModelMainContainerWithProvider: React.FC = () => {
    const changeTimes = useTrackLayoutAppSelector((state) => state.trackLayout.changeTimes);
    return <InfraModelMainContainer changeTimes={changeTimes} />;
};

/*
<Provider store={inframodelStore}>
            <PersistGate loading={null} persistor={persistorIm}>
                <InfraModelMainContainer changeTimes={changeTimes} />
            </PersistGate>
        </Provider>
 */
