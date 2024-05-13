import * as React from 'react';
import {
    useCommonDataAppSelector,
    useInfraModelAppSelector,
    useTrackLayoutAppSelector,
} from 'store/hooks';
import MapView, { MapViewProps } from './map-view';
import { MapContext } from './map-store';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { infraModelActionCreators } from 'infra-model/infra-model-slice';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { GeometryPlanLayout } from 'track-layout/track-layout-model';
import { LayoutContext, officialMainLayoutContext } from 'common/common-model';

const emptyFn = () => void 0;

const getTrackLayoutProps = (): MapViewProps => {
    const store = useTrackLayoutAppSelector((s) => s);
    const changeTimes = useCommonDataAppSelector((s) => s.changeTimes);
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);

    return {
        changeTimes: changeTimes,
        linkingState: store.linkingState,
        splittingState: store.splittingState,
        map: store.map,
        onClickLocation: delegates.onClickLocation,
        onHighlightItems: delegates.onHighlightItems,
        onRemoveGeometryLinkPoint: delegates.removeGeometryLinkPoint,
        onRemoveLayoutLinkPoint: delegates.removeLayoutLinkPoint,
        onSelect: delegates.onSelect,
        onSetGeometryClusterLinkPoint: delegates.setGeometryClusterLinkPoint,
        onSetGeometryPoint: delegates.setGeometryLinkPoint,
        onSetLayoutClusterLinkPoint: delegates.setLayoutClusterLinkPoint,
        onSetLayoutPoint: delegates.setLayoutLinkPoint,
        onShownLayerItemsChange: delegates.onShownItemsChange,
        onViewportUpdate: delegates.onViewportChange,
        layoutContext: store.layoutContext,
        selection: store.selection,
        visibleLayerNames: store.map.visibleLayers,
        mapLayerMenuGroups: store.map.layerMenu,
        onMapLayerChange: delegates.onLayerMenuItemChange,
    };
};

const getInfraModelProps = (): MapViewProps => {
    const store = useInfraModelAppSelector((s) => s);
    const changeTimes = useCommonDataAppSelector((s) => s.changeTimes);
    const delegates = React.useMemo(() => createDelegates(infraModelActionCreators), []);

    return {
        changeTimes: changeTimes,
        linkingState: undefined,
        splittingState: undefined,
        map: store.map,
        onClickLocation: emptyFn,
        onHighlightItems: delegates.onHighlightItems,
        onRemoveGeometryLinkPoint: emptyFn,
        onRemoveLayoutLinkPoint: emptyFn,
        onSelect: delegates.onSelect,
        onSetGeometryClusterLinkPoint: emptyFn,
        onSetGeometryPoint: emptyFn,
        onSetLayoutClusterLinkPoint: emptyFn,
        onSetLayoutPoint: emptyFn,
        onShownLayerItemsChange: emptyFn,
        onViewportUpdate: delegates.onViewportChange,
        layoutContext: officialMainLayoutContext(),
        selection: store.selection,
        visibleLayerNames: store.map.visibleLayers,
        mapLayerMenuGroups: store.map.layerMenu,
        onMapLayerChange: delegates.onLayerMenuItemChange,
    };
};

type MapViewContainerProps = {
    layoutContext?: LayoutContext;
    hoveredOverPlanSection?: HighlightedAlignment;
    manuallySetPlan?: GeometryPlanLayout;
};
export const MapViewContainer: React.FC<MapViewContainerProps> = ({
    layoutContext,
    hoveredOverPlanSection,
    manuallySetPlan,
}) => {
    const mapContext = React.useContext(MapContext);

    const mapProps = mapContext === 'track-layout' ? getTrackLayoutProps() : getInfraModelProps();

    mapProps.layoutContext = layoutContext ? layoutContext : mapProps.layoutContext;
    mapProps.hoveredOverPlanSection = hoveredOverPlanSection;
    mapProps.manuallySetPlan = manuallySetPlan;

    return <MapView {...mapProps} />;
};
