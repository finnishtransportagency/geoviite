import { connect } from 'react-redux';
import InfraModelLoadButton from 'infra-model/list/infra-model-load-button';
import { TrackLayoutAppDispatch } from 'store/store';
import { actionCreators } from 'infra-model/infra-model-store';
import { createDelegates } from 'store/store-utils';

function mapDispatchToProps(dispatch: TrackLayoutAppDispatch) {
    const delegates = createDelegates(dispatch, actionCreators);

    return {
        onFileSelected: delegates.setInfraModelFile,
    };
}

export const InfraModelLoadButtonContainer = connect(
    undefined,
    mapDispatchToProps,
)(InfraModelLoadButton);
