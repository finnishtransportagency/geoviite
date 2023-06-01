import { infraModelActionCreators, InfraModelViewType } from '../infra-model-slice';
import { createDelegates } from 'store/store-utils';
import { InfraModelView } from 'infra-model/view/infra-model-view';
import {
    GeometryElement,
    GeometryElementId,
    GeometrySwitch,
    GeometrySwitchId,
} from 'geometry/geometry-model';
import { getGeometryElementFromPlan, getGeometrySwitchFromPlan } from 'geometry/geometry-utils';
import React from 'react';
import { InfraModelEditLoader } from 'infra-model/view/infra-model-edit-loader';
import { useCommonDataAppSelector, useInfraModelAppSelector } from 'store/hooks';

type InfraModelViewContainerProps = {
    viewType: InfraModelViewType;
};

export const InfraModelViewContainer: React.FC<InfraModelViewContainerProps> = ({
    viewType,
}: InfraModelViewContainerProps) => {
    const infraModelState = useInfraModelAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const delegates = createDelegates(infraModelActionCreators);

    const delegatesProps = {
        onShownLayerItemsChange: () => undefined,
        onInfraModelExtraParametersChange: delegates.onInfraModelExtraParametersChange,
        onInfraModelOverrideParametersChange: delegates.onInfraModelOverrideParametersChange,
        onPlanUpdate: delegates.onPlanUpdate,
        onPlanFetchReady: delegates.onPlanFetchReady,
        onSelect: delegates.onSelect,
        onHighlightItems: delegates.onHighlightItems,
        onClickLocation: delegates.onClickLocation,
        onViewportChange: delegates.onViewportChange,
        onCommitField: delegates.onCommitField,
        showArea: delegates.showArea,
        setExistingInfraModel: delegates.setExistingInfraModel,
        getGeometryElement: async (
            geomElemId: GeometryElementId,
        ): Promise<GeometryElement | null> => {
            return infraModelState.plan
                ? getGeometryElementFromPlan(infraModelState.plan, geomElemId)
                : null;
        },
        getGeometrySwitch: async (
            geometrySwitchId: GeometrySwitchId,
        ): Promise<GeometrySwitch | null> => {
            return infraModelState.plan
                ? getGeometrySwitchFromPlan(infraModelState.plan, geometrySwitchId)
                : null;
        },
    };

    return viewType == InfraModelViewType.UPLOAD ? (
        <InfraModelView
            {...infraModelState}
            {...delegatesProps}
            changeTimes={changeTimes}
            viewType={viewType}
        />
    ) : (
        <InfraModelEditLoader
            {...infraModelState}
            {...delegatesProps}
            changeTimes={changeTimes}
            viewType={viewType}
        />
    );
};
