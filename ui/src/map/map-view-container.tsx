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
import { PublishType } from 'common/common-model';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';

const emptyFn = () => void 0;

const getTrackLayoutProps = (): MapViewProps => {
    const store = useTrackLayoutAppSelector((s) => s);
    const changeTimes = useCommonDataAppSelector((s) => s.changeTimes);
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);

    return {
        changeTimes: changeTimes,
        linkingState: store.linkingState,
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
        publishType: store.publishType,
        selection: store.selection,
        onDoneLoading: delegates.onDoneLoading,
    };
};

const getInfraModelProps = (): MapViewProps => {
    const store = useInfraModelAppSelector((s) => s);
    const changeTimes = useCommonDataAppSelector((s) => s.changeTimes);
    const delegates = React.useMemo(() => createDelegates(infraModelActionCreators), []);

    return {
        changeTimes: changeTimes,
        linkingState: undefined,
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
        publishType: 'OFFICIAL',
        selection: store.selection,
        onDoneLoading: emptyFn,
    };
};

type MapViewContainerProps = {
    publishType?: PublishType;
    hoveredOverPlanSection?: HighlightedAlignment;
};
export const MapViewContainer: React.FC<MapViewContainerProps> = ({
    publishType,
    hoveredOverPlanSection,
}) => {
    const mapContext = React.useContext(MapContext);

    const mapProps = mapContext === 'track-layout' ? getTrackLayoutProps() : getInfraModelProps();

    mapProps.publishType = publishType ? publishType : mapProps.publishType;
    mapProps.hoveredOverPlanSection = hoveredOverPlanSection;

    return <MapView {...mapProps} />;
};
