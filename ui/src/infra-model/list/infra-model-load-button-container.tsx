import React from 'react';
import InfraModelLoadButton from 'infra-model/list/infra-model-load-button';
import { infraModelActionCreators } from 'infra-model/infra-model-slice';
import { createDelegates } from 'store/store-utils';
import { useTrackLayoutAppDispatch } from 'store/hooks';

/*
function mapDispatchToProps(dispatch: TrackLayoutAppDispatch) {
    const delegates = createDelegates(dispatch, infraModelActionCreators);

    return {
        onFileSelected: delegates.setInfraModelFile,
    };
}

 */

export const InfraModelLoadButtonContainer = () => {
    //ongelma: tiedosto ei tallennu storeen

    const trackLayoutAppDispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(trackLayoutAppDispatch, infraModelActionCreators);

    return <InfraModelLoadButton onFileSelected={delegates.setInfraModelFile} />;
};
