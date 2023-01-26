import { connect } from 'react-redux';
import { InfraModelRootState, TrackLayoutAppDispatch } from 'store/store';
import { actionCreators, InfraModelViewType } from '../infra-model-store';
import { createDelegates } from 'store/store-utils';
import { InfraModelView, InfraModelViewProps } from 'infra-model/view/infra-model-view';
import {
    GeometryElement,
    GeometryElementId,
    GeometrySwitch,
    GeometrySwitchId,
} from 'geometry/geometry-model';
import { getGeometryElementFromPlan, getGeometrySwitchFromPlan } from 'geometry/geometry-utils';
import React from 'react';
import {
    InfraModelEditLoader,
    InfraModelLoaderProps,
} from 'infra-model/view/infra-model-edit-loader';

function mapStateToProps({ infraModel }: InfraModelRootState) {
    return {
        ...infraModel,
        getGeometryElement: async (
            geomElemId: GeometryElementId,
        ): Promise<GeometryElement | null> => {
            return infraModel.plan ? getGeometryElementFromPlan(infraModel.plan, geomElemId) : null;
        },
        getGeometrySwitch: async (
            geometrySwitchId: GeometrySwitchId,
        ): Promise<GeometrySwitch | null> => {
            return infraModel.plan
                ? getGeometrySwitchFromPlan(infraModel.plan, geometrySwitchId)
                : null;
        },
    };
}

function mapDispatchToProps(dispatch: TrackLayoutAppDispatch) {
    const delegates = createDelegates(dispatch, actionCreators);

    return {
        onInfraModelExtraParametersChange: delegates.onInfraModelExtraParametersChange,
        onInfraModelOverrideParametersChange: delegates.onInfraModelOverrideParametersChange,
        onPlanUpdate: delegates.onPlanUpdate,
        onPlanFetchReady: delegates.onPlanFetchReady,
        onSelect: delegates.onSelect,
        onHighlightItems: delegates.onHighlightItems,
        onHoverLocation: delegates.onHoverLocation,
        onClickLocation: delegates.onClickLocation,
        onViewportChange: delegates.onViewportChange,
        onCommitField: delegates.onCommitField,
        showArea: delegates.showArea,
        setExistingInfraModel: delegates.setExistingInfraModel,
    };
}

const InfraModelLoadingInfraModelView: React.FC<InfraModelViewProps> = (
    props: InfraModelLoaderProps,
) => {
    return props.viewType == InfraModelViewType.UPLOAD ? (
        <InfraModelView {...props} />
    ) : (
        <InfraModelEditLoader {...props} />
    );
};

export const InfraModelViewContainer = connect(
    mapStateToProps,
    mapDispatchToProps,
)(InfraModelLoadingInfraModelView);
