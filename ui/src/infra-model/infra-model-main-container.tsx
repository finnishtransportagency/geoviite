import { connect, Provider } from 'react-redux';
import { InfraModelMainView } from 'infra-model/infra-model-main-view';
import { InfraModelRootState, inframodelStore } from 'store/store';
import React from 'react';
import { PersistGate } from 'redux-persist/integration/react';
import { persistStore } from 'redux-persist';
import { useTrackLayoutAppSelector } from 'store/hooks';

const persistorIm = persistStore(inframodelStore);

function mapStateToProps({ infraModel }: InfraModelRootState) {
    return {
        viewType: infraModel.viewType,
    };
}

const InfraModelMainContainer = connect(mapStateToProps)(InfraModelMainView);

export const InfraModelMainContainerWithProvider: React.FC = () => {
    const changeTimes = useTrackLayoutAppSelector((state) => state.trackLayout.changeTimes);
    return (
        <Provider store={inframodelStore}>
            <PersistGate loading={null} persistor={persistorIm}>
                <InfraModelMainContainer changeTimes={changeTimes} />
            </PersistGate>
        </Provider>
    );
};
