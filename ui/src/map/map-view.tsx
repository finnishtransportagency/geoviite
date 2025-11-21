import React from 'react';
import OlMap from 'ol/Map';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnSelectFunction,
    Selection,
} from 'selection/selection-model';
import { defaults as defaultInteractions, Draw } from 'ol/interaction';
import DragPan from 'ol/interaction/DragPan.js';
import 'ol/ol.css';
import OlView from 'ol/View';
import {
    getLayerSetting,
    HELSINKI_RAILWAY_STATION_COORDS,
    Map,
    MapLayerMenuChange,
    MapLayerMenuGroups,
    MapLayerName,
    MapViewport,
    OptionalShownItems,
} from 'map/map-model';
import { createSwitchLinkingLayer } from './layers/switch/switch-linking-layer';
import styles from './map.module.scss';
import { MapTool, MapToolActivateOptions, MapToolWithButton } from './tools/tool-model';
import { calculateMapTiles } from 'map/map-utils';
import { defaults as defaultControls, ScaleLine } from 'ol/control';
import { LineString, Point as OlPoint, Polygon as OlPolygon } from 'ol/geom';
import {
    LinkingState,
    LinkingSwitch,
    LinkPoint,
    PlacingOperationalPoint,
    PlacingOperationalPointArea,
} from 'linking/linking-model';
import { pointLocationTool } from 'map/tools/point-location-tool';
import { LocationHolderView } from 'map/location-holder/location-holder-view';
import { GeometryPlanLayout, LAYOUT_SRID, LayoutGraphLevel } from 'track-layout/track-layout-model';
import { LayoutContext, LayoutContextMode, LayoutDesignId } from 'common/common-model';
import Overlay from 'ol/Overlay';
import { useTranslation } from 'react-i18next';
import { createDebugLayer } from 'map/layers/debug/debug-layer';
import { createDebug1mPointsLayer } from './layers/debug/debug-1m-points-layer';
import { createClassName } from 'vayla-design-lib/utils';
import { ChangeTimes } from 'common/common-slice';
import { createTrackNumberDiagramLayer } from 'map/layers/highlight/track-number-diagram-layer';
import { createGeometryAlignmentLayer } from 'map/layers/geometry/geometry-alignment-layer';
import { createGeometryKmPostLayer } from 'map/layers/geometry/geometry-km-post-layer';
import { createKmPostLayer } from 'map/layers/km-post/km-post-layer';
import { createAlignmentLinkingLayer } from 'map/layers/alignment/alignment-linking-layer';
import { createPlanAreaLayer } from 'map/layers/geometry/plan-area-layer';
import { GeoviiteMapLayer, getFeatureCoords, pointToCoords } from 'map/layers/utils/layer-utils';
import { createGeometrySwitchLayer } from 'map/layers/geometry/geometry-switch-layer';
import { createSwitchLayer } from 'map/layers/switch/switch-layer';
import { createBackgroundMapLayer, createOrthographicMapLayer, } from 'map/layers/background-map-layer';
import TileSource from 'ol/source/Tile';
import TileLayer from 'ol/layer/Tile';
import { MapLayer } from 'map/layers/utils/layer-model';
import { filterNotEmpty, first, objectEntries } from 'utils/array-utils';
import { mapLayerZIndexes, REFERENCE_LINE_AUTO_HIDE_MAX_RESOLUTION, } from 'map/layers/utils/layer-visibility-limits';
import { createLocationTrackAlignmentLayer } from 'map/layers/alignment/location-track-alignment-layer';
import { createReferenceLineAlignmentLayer } from 'map/layers/alignment/reference-line-alignment-layer';
import { createLocationTrackBackgroundLayer } from 'map/layers/alignment/location-track-background-layer';
import { createReferenceLineBackgroundLayer } from 'map/layers/alignment/reference-line-background-layer';
import { createReferenceLineBadgeLayer } from 'map/layers/alignment/reference-line-badge-layer';
import { createLocationTrackBadgeLayer } from 'map/layers/alignment/location-track-badge-layer';
import { createDuplicateTracksHighlightLayer } from 'map/layers/highlight/duplicate-tracks-highlight-layer';
import { createMissingLinkingHighlightLayer } from 'map/layers/highlight/missing-linking-highlight-layer';
import { createMissingProfileHighlightLayer } from 'map/layers/highlight/missing-profile-highlight-layer';
import { createTrackNumberEndPointAddressesLayer } from 'map/layers/highlight/track-number-end-point-addresses-layer';
import { coordsToPolygon, Point, Polygon, Rectangle } from 'model/geometry';
import { createPlanSectionHighlightLayer } from 'map/layers/highlight/plan-section-highlight-layer';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { createLocationTrackSplitLocationLayer } from 'map/layers/alignment/location-track-split-location-layer';
import {
    createDuplicateSplitSectionHighlightLayer
} from 'map/layers/highlight/duplicate-split-section-highlight-layer';
import {
    createDuplicateTrackEndpointAddressLayer
} from 'map/layers/alignment/location-track-duplicate-endpoint-indicator-layer';
import {
    createLocationTrackSelectedAlignmentLayer
} from 'map/layers/alignment/location-track-selected-alignment-layer';
import { createLocationTrackSplitBadgeLayer } from 'map/layers/alignment/location-track-split-badge-layer';
import { createSelectedReferenceLineAlignmentLayer } from './layers/alignment/reference-line-selected-alignment-layer';
import { createOperationalPointIconLayer } from 'map/layers/operational-point/operational-points-icon-layer';
import { layersCoveringLayers } from 'map/map-store';
import { createLocationTrackSplitAlignmentLayer } from 'map/layers/alignment/location-track-split-alignment-layer';
import { MapLayerMenu } from 'map/layer-menu/map-layer-menu';
import { createPublicationCandidateLayer } from 'map/layers/preview/publication-candidate-layer';
import { PublicationCandidate } from 'publication/publication-model';
import { DesignPublicationMode } from 'preview/preview-tool-bar';
import {
    createDeletedPublicationCandidateIconLayer
} from 'map/layers/preview/deleted-publication-candidate-icon-layer';
import { useResizeObserver } from 'utils/use-resize-observer';
import { createDebugGeometryGraphLayer } from 'map/layers/debug/debug-geometry-graph-layer';
import { PlanDownloadState } from 'map/plan-download/plan-download-store';
import { PlanDownloadPopup } from 'map/plan-download/plan-download-popup';
import { createDebugProjectionLinesLayer } from 'map/layers/debug/debug-projection-lines-layer';
import {
    createOperationalPointsAreaPlacingLayer
} from 'map/layers/operational-point/operational-points-area-placing-layer';
import Feature from 'ol/Feature';
import { createOperationalPointsPlacingLayer } from 'map/layers/operational-point/operational-points-placing-layer';
import { operationalPointPolygonStylesFunc } from 'map/layers/operational-point/operational-points-layer-utils';
import { createOperationalPointAreaLayer } from 'map/layers/operational-point/operational-points-area-layer';
import { createOperationalPointBadgeLayer } from 'map/layers/operational-point/operational-points-badge-layer';
import { createSignalAssetLayer } from 'map/layers/ratko/signal-asset-layer';

declare global {
    interface Window {
        map: OlMap;
    }
}

export type MapViewProps = {
    map: Map;
    selection: Selection;
    layoutContext: LayoutContext;
    linkingState: LinkingState | undefined;
    splittingState: SplittingState | undefined;
    planDownloadState: PlanDownloadState | undefined;
    onSelect: OnSelectFunction;
    changeTimes: ChangeTimes;
    onHighlightItems: OnHighlightItemsFunction;
    onClickLocation: OnClickLocationFunction;
    onViewportUpdate: (viewport: MapViewport) => void;
    onShownLayerItemsChange: (items: OptionalShownItems) => void;
    onSetLayoutPoint: (linkPoint: LinkPoint) => void;
    onSetGeometryPoint: (linkPoint: LinkPoint) => void;
    onSetLayoutClusterLinkPoint: (linkPoint: LinkPoint) => void;
    onSetGeometryClusterLinkPoint: (linkPoint: LinkPoint) => void;
    onRemoveGeometryLinkPoint: (linkPoint: LinkPoint) => void;
    onRemoveLayoutLinkPoint: (linkPoint: LinkPoint) => void;
    onClosePlanDownloadPopup: () => void;
    onSetOperationalPointPolygon: (polygon: Polygon) => void;
    hoveredOverPlanSection?: HighlightedAlignment | undefined;
    manuallySetPlan?: GeometryPlanLayout;
    onMapLayerChange: (change: MapLayerMenuChange) => void;
    mapLayerMenuGroups: MapLayerMenuGroups;
    visibleLayerNames: MapLayerName[];
    publicationCandidates?: PublicationCandidate[];
    customActiveMapTool?: MapTool;
    designPublicationMode?: DesignPublicationMode;
    mapTools?: MapToolWithButton[];
    layoutContextMode?: LayoutContextMode;
    selectedDesignId?: LayoutDesignId;
};

export type ClickType = 'all' | 'geometryPoint' | 'layoutPoint' | 'remove';

const defaultScaleLine: ScaleLine = new ScaleLine({
    units: 'metric',
    minWidth: 80,
});

function getOlViewByDomainViewport(viewport: MapViewport): OlView {
    return new OlView({
        center: [
            viewport.center?.x ?? HELSINKI_RAILWAY_STATION_COORDS.x,
            viewport.center?.y ?? HELSINKI_RAILWAY_STATION_COORDS.y,
        ],
        resolution: viewport.resolution,
        maxZoom: 32,
        minZoom: 6,
        projection: LAYOUT_SRID,
        smoothResolutionConstraint: true,
    });
}

function getDomainViewportByOlView(map: OlMap): MapViewport {
    const view = map.getView();
    const center = view.getCenter() as [number, number] | undefined;
    const extent = map.getView().calculateExtent() as [number, number, number, number];
    return {
        center: center
            ? {
                  x: center[0],
                  y: center[1],
              }
            : undefined,
        resolution: view.getResolution() as number,
        area: {
            x: { min: extent[0], max: extent[2] },
            y: { min: extent[1], max: extent[3] },
        },
        source: 'Map',
    };
}

function referenceLineHideWhenZoomedCloseSetting(mapLayerMenuGroups: MapLayerMenuGroups): boolean {
    return getLayerSetting(
        mapLayerMenuGroups.layout,
        'reference-line',
        'reference-line-hide-when-zoomed-close',
    );
}

function getLayoutGraphLevel(mapLayerMenuGroups: MapLayerMenuGroups): LayoutGraphLevel {
    return getLayerSetting(
        mapLayerMenuGroups.debug,
        'debug-layout-graph',
        'debug-layout-graph-nano',
    )
        ? 'NANO'
        : 'MICRO';
}

function anyLayerIsLoading(
    visibleLayers: MapLayerName[],
    layersLoadingData: Set<MapLayerName>,
): boolean {
    return visibleLayers.some((l) => layersLoadingData.has(l));
}

function useIsLoadingMapLayers(visibleLayers: MapLayerName[]): {
    onLayerLoading: (name: MapLayerName, layerIsLoading: boolean) => void;
    isLoading: boolean;
} {
    const layersLoadingData = React.useRef<Set<MapLayerName>>(new Set());
    const [isLoading, setIsLoading] = React.useState(false);

    const onLayerLoading = React.useCallback(
        (name: MapLayerName, layerIsLoading: boolean) => {
            if (layerIsLoading) {
                layersLoadingData.current.add(name);
            } else {
                layersLoadingData.current.delete(name);
            }
            setIsLoading(anyLayerIsLoading(visibleLayers, layersLoadingData.current));
        },
        [visibleLayers],
    );
    React.useEffect(() => {
        setIsLoading(anyLayerIsLoading(visibleLayers, layersLoadingData.current));
    }, [visibleLayers]);
    return { isLoading, onLayerLoading };
}

const createOperationalPointAreaDrawInteraction = (
    onSetOperationalPointPolygon: (polygon: Polygon) => void,
    linkingState: LinkingState | undefined,
): Draw => {
    const draw = new Draw({
        type: 'Polygon',
        style: operationalPointPolygonStylesFunc('SELECTED', 'ADDING'),
    });
    draw.on('drawend', function (event) {
        const feature = event.feature as Feature<OlPolygon>;
        if (!feature) {
            return;
        }

        const coords = getFeatureCoords(feature);
        onSetOperationalPointPolygon(coordsToPolygon(coords));
    });
    draw.setActive(linkingState?.type === 'PlacingOperationalPointArea' && !linkingState.area);

    return draw;
};
const MapView: React.FC<MapViewProps> = ({
    map,
    selection,
    layoutContext,
    linkingState,
    splittingState,
    planDownloadState,
    changeTimes,
    onSelect,
    onViewportUpdate,
    hoveredOverPlanSection,
    manuallySetPlan,
    onSetLayoutClusterLinkPoint,
    onSetGeometryClusterLinkPoint,
    onRemoveLayoutLinkPoint,
    onRemoveGeometryLinkPoint,
    onShownLayerItemsChange,
    onHighlightItems,
    onClosePlanDownloadPopup,
    onClickLocation,
    onMapLayerChange,
    onSetOperationalPointPolygon,
    mapLayerMenuGroups,
    visibleLayerNames,
    publicationCandidates,
    customActiveMapTool,
    designPublicationMode,
    mapTools,
    layoutContextMode,
    selectedDesignId,
}: MapViewProps) => {
    const { t } = useTranslation();
    // State to store OpenLayers map object between renders
    const [olMap, setOlMap] = React.useState<OlMap>();
    const olMapContainer = React.useRef<HTMLDivElement>(null);
    const [visibleLayers, setVisibleLayers] = React.useState<MapLayer[]>([]);
    const [activeTool, setActiveTool] = React.useState<MapTool | undefined>(
        customActiveMapTool || (mapTools && first(mapTools)),
    );
    const [hoveredLocation, setHoveredLocation] = React.useState<Point>();
    const inPreviewView = !!designPublicationMode;
    const isSelectingDesign = layoutContextMode === 'DESIGN' && !selectedDesignId;
    const { isLoading, onLayerLoading } = useIsLoadingMapLayers(map.visibleLayers);
    const mapLayers = [...map.visibleLayers].sort().join();
    const [operationalPointAreaDrawInteraction, setOperationalPointAreaDrawInteraction] =
        React.useState<Draw>();

    const handleClusterPointClick = (clickType: ClickType) => {
        const clusterPoint = first(selection.selectedItems.clusterPoints);
        if (clusterPoint) {
            switch (clickType) {
                case 'all':
                    onSetLayoutClusterLinkPoint(clusterPoint.layoutPoint);
                    onSetGeometryClusterLinkPoint(clusterPoint.geometryPoint);
                    break;
                case 'geometryPoint':
                    onSetGeometryClusterLinkPoint(clusterPoint.geometryPoint);
                    onRemoveLayoutLinkPoint(clusterPoint.layoutPoint);
                    break;
                case 'layoutPoint':
                    onSetLayoutClusterLinkPoint(clusterPoint.layoutPoint);
                    onRemoveGeometryLinkPoint(clusterPoint.geometryPoint);
                    break;
                case 'remove':
                    onRemoveLayoutLinkPoint(clusterPoint.layoutPoint);
                    onRemoveGeometryLinkPoint(clusterPoint.geometryPoint);
                    break;
                default:
                    return exhaustiveMatchingGuard(clickType);
            }
        }
    };

    useResizeObserver({
        ref: olMapContainer,
        onResize: () => olMap?.updateSize(),
    });

    // Initialize OpenLayers map. Do this only once, in subsequent
    // renders we just want to update OpenLayers layers. In this way map
    // works smoothly.
    React.useEffect(() => {
        if (olMapContainer.current) {
            olMapContainer.current.innerHTML = '';

            const controls = defaultControls();
            controls.extend([defaultScaleLine]);
            const interactions = defaultInteractions();
            //Mouse middle click pan
            interactions.push(
                new DragPan({ condition: (event) => event.originalEvent.which === 2 }),
            );

            const draw = createOperationalPointAreaDrawInteraction(
                onSetOperationalPointPolygon,
                linkingState,
            );
            setOperationalPointAreaDrawInteraction(draw);
            interactions.push(draw);

            // use in the browser window.map.getPixelFromCoordinate([x,y])
            window.map = new OlMap({
                controls: controls,
                interactions: interactions,
                target: olMapContainer.current as HTMLElement,
                view: getOlViewByDomainViewport(map.viewport),
            });

            setOlMap(window.map);
        }

        return () => {
            if (operationalPointAreaDrawInteraction) {
                olMap?.removeInteraction(operationalPointAreaDrawInteraction);
            }
        };
    }, []);

    React.useEffect(() => {
        if (linkingState?.type === 'PlacingOperationalPointArea') {
            operationalPointAreaDrawInteraction?.setActive(!linkingState.area);
        } else {
            operationalPointAreaDrawInteraction?.setActive(false);
        }
    }, [linkingState, olMap]);

    // Track map view port changes
    React.useEffect(() => {
        if (!olMap) return;

        const listenerInfo = olMap.on('moveend', () => {
            onViewportUpdate(getDomainViewportByOlView(olMap));
        });

        return () => {
            olMap.un('moveend', listenerInfo.listener);
        };
    }, [olMap]);

    const firstClusterPoint = first(selection.selectedItems.clusterPoints);
    React.useEffect(() => {
        if (!olMap || !firstClusterPoint) return;
        const pos = pointToCoords(firstClusterPoint);
        const popupElement = document.getElementById('clusteroverlay') || undefined;
        const popup = new Overlay({
            position: pos,
            offset: [7, 0],
            element: popupElement,
        });
        olMap.addOverlay(popup);
    }, [olMap, firstClusterPoint]);

    // Update the view"port" of the map
    React.useEffect(() => {
        if (!olMap) {
            return;
        }

        // Ignore viewport changes made by the map itself.
        // Without this check the map can get into invalid state if it is
        // quickly panned (moved) back and forth, and surprisingly this
        // happens quite easily in real life.
        if (map.viewport.source !== 'Map') {
            olMap.setView(getOlViewByDomainViewport(map.viewport));
        }
    }, [olMap, map.viewport]);

    // Convert layer domain models into OpenLayers layers
    React.useEffect(() => {
        if (!olMap) return;

        const olView = olMap.getView();
        const resolution = olView.getResolution() || 0;

        const layerIsCovered = (layer: MapLayerName) =>
            objectEntries(layersCoveringLayers).some(
                ([coveringLayer, covers]) =>
                    map.visibleLayers.includes(coveringLayer) && covers?.includes(layer),
            );

        const hideReferenceLinesWhenZoomedClose =
            referenceLineHideWhenZoomedCloseSetting(mapLayerMenuGroups) &&
            resolution <= REFERENCE_LINE_AUTO_HIDE_MAX_RESOLUTION;

        // Create OpenLayers objects by domain layers
        const updatedLayers = map.visibleLayers
            .filter((layer) => !layerIsCovered(layer))
            .map((layerName) => {
                const mapTiles = calculateMapTiles(olView, undefined);

                // Step 2. create the layer
                // In some cases an adapter wants to reuse existing OL layer,
                // e.g. tile layers cause flickering if recreated every time
                const existingOlLayer = visibleLayers.find((l) => l.name === layerName)?.layer;

                switch (layerName) {
                    case 'background-map-layer':
                        return createBackgroundMapLayer(existingOlLayer as TileLayer<TileSource>);
                    case 'orthographic-background-map-layer':
                        return createOrthographicMapLayer(existingOlLayer as TileLayer<TileSource>);
                    case 'track-number-diagram-layer':
                        return createTrackNumberDiagramLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            changeTimes,
                            layoutContext,
                            map.layerSettings['track-number-diagram-layer'],
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'deleted-publication-candidate-icon-layer':
                        return inPreviewView
                            ? createDeletedPublicationCandidateIconLayer(
                                  mapTiles,
                                  existingOlLayer as GeoviiteMapLayer<LineString | OlPoint>,
                                  publicationCandidates ?? [],
                                  designPublicationMode,
                                  layoutContext,
                                  changeTimes,
                                  onShownLayerItemsChange,
                                  (loading) => onLayerLoading(layerName, loading),
                                  resolution,
                              )
                            : undefined;
                    case 'publication-candidate-layer':
                        return inPreviewView
                            ? createPublicationCandidateLayer(
                                  mapTiles,
                                  existingOlLayer as GeoviiteMapLayer<LineString>,
                                  changeTimes,
                                  layoutContext,
                                  resolution,
                                  (loading) => onLayerLoading(layerName, loading),
                                  publicationCandidates?.length ? publicationCandidates : [],
                                  designPublicationMode,
                              )
                            : undefined;
                    case 'track-number-addresses-layer':
                        return createTrackNumberEndPointAddressesLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            changeTimes,
                            layoutContext,
                            resolution,
                            map.layerSettings['track-number-diagram-layer'],
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'reference-line-alignment-layer':
                        return hideReferenceLinesWhenZoomedClose
                            ? undefined
                            : createReferenceLineAlignmentLayer(
                                  mapTiles,
                                  existingOlLayer as GeoviiteMapLayer<LineString | OlPoint>,
                                  selection,
                                  !!splittingState,
                                  layoutContext,
                                  changeTimes,
                                  onShownLayerItemsChange,
                                  (loading) => onLayerLoading(layerName, loading),
                              );
                    case 'reference-line-background-layer':
                        return hideReferenceLinesWhenZoomedClose
                            ? undefined
                            : createReferenceLineBackgroundLayer(
                                  mapTiles,
                                  existingOlLayer as GeoviiteMapLayer<LineString>,
                                  !!splittingState,
                                  layoutContext,
                                  changeTimes,
                                  (loading) => onLayerLoading(layerName, loading),
                              );
                    case 'reference-line-badge-layer':
                        return hideReferenceLinesWhenZoomedClose
                            ? undefined
                            : createReferenceLineBadgeLayer(
                                  mapTiles,
                                  existingOlLayer as GeoviiteMapLayer<OlPoint>,
                                  selection,
                                  layoutContext,
                                  linkingState,
                                  changeTimes,
                                  resolution,
                                  (loading) => onLayerLoading(layerName, loading),
                              );
                    case 'location-track-alignment-layer':
                        return createLocationTrackAlignmentLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString | OlPoint>,
                            selection,
                            !!splittingState,
                            layoutContext,
                            changeTimes,
                            olView,
                            onShownLayerItemsChange,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'location-track-background-layer':
                        return createLocationTrackBackgroundLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            layoutContext,
                            changeTimes,
                            !!splittingState,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'location-track-badge-layer':
                        return createLocationTrackBadgeLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            selection,
                            layoutContext,
                            linkingState,
                            changeTimes,
                            resolution,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'missing-linking-highlight-layer':
                        return createMissingLinkingHighlightLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            layoutContext,
                            changeTimes,
                            resolution,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'duplicate-tracks-highlight-layer':
                        return createDuplicateTracksHighlightLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            layoutContext,
                            changeTimes,
                            resolution,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'missing-profile-highlight-layer':
                        return createMissingProfileHighlightLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            layoutContext,
                            changeTimes,
                            resolution,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'plan-section-highlight-layer':
                        return createPlanSectionHighlightLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            layoutContext,
                            changeTimes,
                            resolution,
                            hoveredOverPlanSection,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'km-post-layer':
                        return createKmPostLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPoint | Rectangle>,
                            selection,
                            layoutContext,
                            changeTimes,
                            olView,
                            onShownLayerItemsChange,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'switch-layer':
                        return createSwitchLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            selection,
                            splittingState,
                            layoutContext,
                            changeTimes,
                            olView,
                            onShownLayerItemsChange,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'geometry-alignment-layer':
                        return createGeometryAlignmentLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            selection,
                            layoutContext,
                            changeTimes,
                            resolution,
                            manuallySetPlan,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'geometry-km-post-layer':
                        return createGeometryKmPostLayer(
                            mapTiles,
                            resolution,
                            existingOlLayer as GeoviiteMapLayer<OlPoint | Rectangle>,
                            selection,
                            layoutContext,
                            changeTimes,
                            manuallySetPlan,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'geometry-switch-layer':
                        return createGeometrySwitchLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            selection,
                            layoutContext,
                            changeTimes,
                            resolution,
                            manuallySetPlan,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'alignment-linking-layer':
                        return createAlignmentLinkingLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPoint | LineString>,
                            layoutContext,
                            selection,
                            linkingState,
                            changeTimes,
                            resolution,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'switch-linking-layer':
                        return createSwitchLinkingLayer(
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            linkingState as LinkingSwitch | undefined,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'location-track-split-location-layer':
                        return createLocationTrackSplitLocationLayer(
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            layoutContext,
                            splittingState,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'duplicate-split-section-highlight-layer':
                        return createDuplicateSplitSectionHighlightLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            layoutContext,
                            changeTimes,
                            resolution,
                            splittingState,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'location-track-duplicate-endpoint-address-layer':
                        return createDuplicateTrackEndpointAddressLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            layoutContext,
                            changeTimes,
                            resolution,
                            splittingState,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'location-track-split-badge-layer':
                        return createLocationTrackSplitBadgeLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            layoutContext,
                            splittingState,
                            changeTimes,
                            resolution,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'location-track-selected-alignment-layer':
                        return createLocationTrackSelectedAlignmentLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            selection,
                            layoutContext,
                            splittingState !== undefined,
                            changeTimes,
                            olView,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'location-track-split-alignment-layer':
                        return createLocationTrackSplitAlignmentLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            layoutContext,
                            splittingState,
                            changeTimes,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'reference-line-selected-alignment-layer':
                        return createSelectedReferenceLineAlignmentLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            selection,
                            layoutContext,
                            splittingState !== undefined,
                            changeTimes,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'plan-area-layer':
                        return createPlanAreaLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPolygon>,
                            changeTimes,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'operational-points-icon-layer':
                        return createOperationalPointIconLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            olView,
                            selection,
                            linkingState,
                            layoutContext,
                            changeTimes,
                        );
                    case 'operational-points-badge-layer':
                        return createOperationalPointBadgeLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            olView,
                            selection,
                            linkingState,
                            layoutContext,
                            changeTimes,
                        );
                    case 'operational-points-area-layer':
                        return createOperationalPointAreaLayer(
                            mapTiles,
                            existingOlLayer as GeoviiteMapLayer<OlPolygon>,
                            selection,
                            linkingState,
                            layoutContext,
                            changeTimes,
                        );
                    case 'operational-points-placing-layer':
                        return createOperationalPointsPlacingLayer(
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            linkingState as PlacingOperationalPoint | undefined,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'operational-points-area-placing-layer':
                        return createOperationalPointsAreaPlacingLayer(
                            existingOlLayer as GeoviiteMapLayer<OlPolygon>,
                            linkingState as PlacingOperationalPointArea | undefined,
                            layoutContext,
                            olMap,
                            onSetOperationalPointPolygon,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'debug-1m-points-layer':
                        return createDebug1mPointsLayer(
                            existingOlLayer as GeoviiteMapLayer<OlPoint>,
                            selection,
                            layoutContext,
                            resolution,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'debug-projection-lines-layer':
                        return createDebugProjectionLinesLayer(
                            existingOlLayer as GeoviiteMapLayer<LineString>,
                            selection,
                            layoutContext,
                            resolution,
                            changeTimes,
                            olView,
                            (loading) => onLayerLoading(layerName, loading),
                        );
                    case 'debug-layer':
                        return createDebugLayer(existingOlLayer as GeoviiteMapLayer<OlPoint>);
                    case 'debug-geometry-graph-layer':
                        return createDebugGeometryGraphLayer(
                            existingOlLayer as GeoviiteMapLayer<LineString | OlPoint>,
                            (loading) => onLayerLoading(layerName, loading),
                            layoutContext,
                            mapTiles,
                            resolution,
                            getLayoutGraphLevel(mapLayerMenuGroups),
                        );
                    case 'virtual-km-post-linking-layer': // Virtual map layers
                    case 'virtual-hide-geometry-layer':
                        return undefined;
                    case 'signal-asset-layer':
                        return createSignalAssetLayer(existingOlLayer as TileLayer<TileSource>);
                    default:
                        return exhaustiveMatchingGuard(layerName);
                }
            })
            .filter(filterNotEmpty);

        updatedLayers.forEach((l) => l.layer.setZIndex(mapLayerZIndexes[l.name]));

        visibleLayers
            .filter((vl) => !updatedLayers.some((ul) => ul.name === vl.name))
            .forEach((l) => l.onRemove && l.onRemove());

        setVisibleLayers(updatedLayers);

        // Set converted layers into map object
        const olLayers = updatedLayers.map((l) => l.layer);
        olMap.setLayers(olLayers);
    }, [
        olMap,
        map.viewport,
        mapLayers,
        selection,
        changeTimes,
        layoutContext,
        linkingState,
        splittingState,
        map.layerSettings,
        hoveredOverPlanSection,
        manuallySetPlan,
        mapLayerMenuGroups,
        publicationCandidates,
    ]);

    const toolActivateOptions: MapToolActivateOptions = {
        onSelect: onSelect,
        onHighlightItems: onHighlightItems,
        onHoverLocation: (p) => setHoveredLocation(p),
        onClickLocation: onClickLocation,
    };

    React.useEffect(() => {
        if (!olMap) return;

        const deactivateCallbacks = [
            pointLocationTool.activate(olMap, visibleLayers, toolActivateOptions),
        ];

        // Return function to clean up initialized stuff
        return () => {
            deactivateCallbacks.forEach((f) => f());
        };
    }, [olMap, visibleLayers, activeTool]);

    React.useEffect(() => {
        setActiveTool(customActiveMapTool);
    }, [customActiveMapTool]);

    React.useEffect(() => {
        if (activeTool && olMap) {
            return activeTool.activate(olMap, visibleLayers, toolActivateOptions);
        } else {
            return () => undefined;
        }
    }, [olMap, activeTool, visibleLayers]);

    React.useEffect(() => {
        if (mapTools && activeTool) {
            const newVersionOfTool = mapTools.find((tool) => tool.id === activeTool.id);
            setActiveTool(newVersionOfTool);
        }
    }, [mapTools]);

    const mapClassNames = createClassName(styles.map);

    const cssProperties = {
        ...(activeTool?.customCursor ? { cursor: activeTool.customCursor } : {}),
    };
    return (
        <div className={mapClassNames} style={cssProperties}>
            {mapTools && (
                <ol className="map__map-tools">
                    {mapTools.map((tool) => {
                        const ToolComponent = tool.component;

                        return (
                            <ToolComponent
                                key={tool.id}
                                isActive={activeTool?.id === tool.id}
                                setActiveTool={setActiveTool}
                            />
                        );
                    })}
                </ol>
            )}
            <div
                ref={olMapContainer}
                qa-resolution={olMap?.getView()?.getResolution()}
                className={styles['map__ol-map']}
            />
            <div id="clusteroverlay">
                {first(selection.selectedItems.clusterPoints) && (
                    <div className={styles['map__popup-menu']}>
                        <div
                            className={styles['map__popup-item']}
                            onClick={() => handleClusterPointClick('geometryPoint')}>
                            {t('map-view.cluster-overlay-choose-geometry')}
                        </div>
                        <div
                            className={styles['map__popup-item']}
                            onClick={() => handleClusterPointClick('layoutPoint')}>
                            {t('map-view.cluster-overlay-choose-layout')}
                        </div>
                        <div
                            className={styles['map__popup-item']}
                            onClick={() => handleClusterPointClick('all')}>
                            {t('map-view.cluster-overlay-choose-both')}
                        </div>
                        <div
                            className={styles['map__popup-item']}
                            onClick={() => handleClusterPointClick('remove')}>
                            {t('map-view.cluster-overlay-remove-both')}
                        </div>
                    </div>
                )}
            </div>
            <div id={'maplayermenubutton'} className={'map__layer-menu'}>
                <MapLayerMenu
                    onMenuChange={onMapLayerChange}
                    mapLayerMenuGroups={mapLayerMenuGroups}
                    visibleLayers={visibleLayerNames}
                />
            </div>
            <LocationHolderView
                hoveredCoordinate={hoveredLocation}
                trackNumbers={selection.selectedItems.trackNumbers}
                locationTracks={selection.selectedItems.locationTracks}
                layoutContext={layoutContext}
            />
            {isLoading && (
                <div className={styles['map__loading-spinner']} qa-id="map-loading-spinner">
                    <Spinner />
                </div>
            )}
            {!inPreviewView && !isSelectingDesign && planDownloadState && (
                <PlanDownloadPopup
                    onClose={() => onClosePlanDownloadPopup()}
                    layoutContext={layoutContext}
                />
            )}
        </div>
    );
};

export default MapView;
