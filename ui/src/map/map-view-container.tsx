import * as React from 'react';
import {
    useCommonDataAppSelector,
    useInfraModelAppSelector,
    useLayoutDelegates,
    useTrackLayoutAppSelector,
} from 'store/hooks';
import MapView, { MapViewProps } from './map-view';
import { MapContext } from './map-store';
import { createDelegates } from 'store/store-utils';
import { infraModelActionCreators } from 'infra-model/infra-model-slice';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { GeometryPlanLayout } from 'track-layout/track-layout-model';
import { LayoutContext, officialMainLayoutContext } from 'common/common-model';
import { PublicationCandidate } from 'publication/publication-model';
import { MapToolId, MapToolWithButton } from 'map/tools/tool-model';
import { DesignPublicationMode } from 'preview/preview-tool-bar';
import { RouteResult } from 'track-layout/layout-routing-api';
import { RouteLocation } from 'track-layout/track-layout-slice';

const emptyFn = () => void 0;

const getTrackLayoutProps = (): MapViewProps => {
    const store = useTrackLayoutAppSelector((s) => s);
    const changeTimes = useCommonDataAppSelector((s) => s.changeTimes);
    const delegates = useLayoutDelegates();

    return {
        changeTimes: changeTimes,
        linkingState: store.linkingState,
        splittingState: store.splittingState,
        planDownloadState: store.planDownloadState,
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
        onSetOperationalPointPolygon: delegates.setOperationalPointArea,
        layoutContext: store.layoutContext,
        selection: store.selection,
        onMapLayerChange: delegates.onLayerMenuItemChange,
        onClosePlanDownloadPopup: delegates.onClosePlanDownloadPopup,
        selectedDesignId: store.designId,
        layoutContextMode: store.layoutContextMode,
        routeLocations: store.routeLocations,
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
        planDownloadState: undefined,
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
        onClosePlanDownloadPopup: emptyFn,
        onSetOperationalPointPolygon: emptyFn,
        onViewportUpdate: delegates.onViewportChange,
        layoutContext: officialMainLayoutContext(),
        selection: store.selection,
        onMapLayerChange: delegates.onLayerMenuItemChange,
    };
};

export type PublicationCandidateState = 'non-staged' | 'staged';

type MapViewContainerProps = {
    layoutContext?: LayoutContext;
    hoveredOverPlanSection?: HighlightedAlignment;
    routeResult?: RouteResult;
    manuallySetPlan?: GeometryPlanLayout;
    publicationCandidates?: PublicationCandidate[];
    customActiveMapToolId?: MapToolId;
    designPublicationMode?: DesignPublicationMode;
    mapTools: MapToolWithButton[];
    hoveredRouteLocation?: RouteLocation;
};
export const MapViewContainer: React.FC<MapViewContainerProps> = ({
    layoutContext,
    hoveredOverPlanSection,
    routeResult,
    manuallySetPlan,
    publicationCandidates,
    customActiveMapToolId,
    designPublicationMode,
    mapTools,
    hoveredRouteLocation,
}) => {
    const mapContext = React.useContext(MapContext);

    const mapProps = mapContext === 'track-layout' ? getTrackLayoutProps() : getInfraModelProps();

    mapProps.layoutContext = layoutContext ? layoutContext : mapProps.layoutContext;
    mapProps.hoveredOverPlanSection = hoveredOverPlanSection;
    mapProps.routeResult = routeResult;
    mapProps.manuallySetPlan = manuallySetPlan;
    mapProps.publicationCandidates = publicationCandidates;
    mapProps.customActiveMapToolId = customActiveMapToolId;
    mapProps.designPublicationMode = designPublicationMode;
    mapProps.mapTools = mapTools;
    mapProps.hoveredRouteLocation = hoveredRouteLocation;

    return <MapView {...mapProps} />;
};
