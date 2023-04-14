import React from 'react';
import InfraModelLoadButton from 'infra-model/list/infra-model-load-button';
import { infraModelActionCreators } from 'infra-model/infra-model-slice';
import { createDelegates } from 'store/store-utils';

export const InfraModelLoadButtonContainer = () => {
    const delegates = createDelegates(infraModelActionCreators);

    return <InfraModelLoadButton onFileSelected={delegates.setInfraModelFile} />;
};
