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
import { useInfraModelAppSelector, useAppDispatch, useAppSelector } from 'store/hooks';

type InfraModelViewContainerProps = {
    viewType: InfraModelViewType;
};

export const InfraModelViewContainer: React.FC<InfraModelViewContainerProps> = ({
    viewType,
}: InfraModelViewContainerProps) => {
    const infraModelState = useInfraModelAppSelector((state) => state);
    const infraModelDispatch = useAppDispatch();
    const changeTimes = useAppSelector((state) => state.trackLayout.changeTimes);

    const delegates = createDelegates(infraModelDispatch, infraModelActionCreators);

    const delegatesProps = {
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

    //Tähän avaa propsit sellaisenaan jotta selvempää mitä laitetaan eteenpäin
    return viewType == InfraModelViewType.UPLOAD ? (
        <InfraModelView
            {...infraModelState}
            changeTimes={changeTimes}
            viewType={viewType}
            {...delegatesProps}
        />
    ) : (
        <InfraModelEditLoader
            {...infraModelState}
            changeTimes={changeTimes}
            viewType={viewType}
            {...delegatesProps}
        />
    );
};

/*
viewType: InfraModelViewType;
    onInfraModelExtraParametersChange: <TKey extends keyof ExtraInfraModelParameters>(
        infraModelExtraParameters: Prop<ExtraInfraModelParameters, TKey>,
    ) => void;
    onInfraModelOverrideParametersChange: (
        overrideInfraModelParameters: OverrideInfraModelParameters,
    ) => void;
    onPlanUpdate: () => void;
    onPlanFetchReady: (plan: OnPlanFetchReady) => void;
    onViewportChange: (viewport: MapViewport) => void;
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
    onSelect: OnSelectFunction;
    changeTimes: ChangeTimes;
    onHighlightItems: OnHighlightItemsFunction;
    getGeometryElement: (geomElemId: GeometryElementId) => Promise<GeometryElement | null>;
    getGeometrySwitch: (geomSwitchId: GeometrySwitchId) => Promise<GeometrySwitch | null>;
    onCommitField: (fieldName: string) => void;
 */

//export const InfraModelViewContainer

//korvaa hooksilla
/*
export const InfraModelViewContainer = connect(
    mapStateToProps, //mapStateToProps is used for selecting the part of the data from the store that the connected component needs.
    mapDispatchToProps, //mapDispatchToProps is used for dispatching actions to the store
)(InfraModelViewContainer);
*/
