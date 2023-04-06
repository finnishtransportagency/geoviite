import React from 'react';
import InfraModelLoadButton from 'infra-model/list/infra-model-load-button';
import { infraModelActionCreators } from 'infra-model/infra-model-slice';
import { createDelegates } from 'store/store-utils';
import { useAppDispatch } from 'store/hooks';

export const InfraModelLoadButtonContainer = () => {
    const dispatch = useAppDispatch();
    const delegates = createDelegates(dispatch, infraModelActionCreators);

    return <InfraModelLoadButton onFileSelected={delegates.setInfraModelFile} />;
};
