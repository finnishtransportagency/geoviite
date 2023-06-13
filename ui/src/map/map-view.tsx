import React from 'react';
import OlMap from 'ol/Map';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnSelectFunction,
    Selection,
} from 'selection/selection-model';
import { defaults } from 'ol/interaction';
import DragPan from 'ol/interaction/DragPan.js';
import 'ol/ol.css';
import OlView from 'ol/View';
import { Map, MapViewport, OptionalShownItems } from 'map/map-model';
import { createSwitchLinkingLayer } from './layers/switch/switch-linking-layer';
import styles from './map.module.scss';
import { selectTool } from './tools/select-tool';
import { MapToolActivateOptions } from './tools/tool-model';
import { calculateMapTiles } from 'map/map-utils';
import { defaults as defaultControls, ScaleLine } from 'ol/control';
import { highlightTool } from 'map/tools/highlight-tool';
import Polygon from 'ol/geom/Polygon';
import { LinkingState, LinkingSwitch, LinkPoint } from 'linking/linking-model';
import { pointLocationTool } from 'map/tools/point-location-tool';
import { LocationHolderView } from 'map/location-holder/location-holder-view';
import { LAYOUT_SRID } from 'track-layout/track-layout-model';
import { PublishType } from 'common/common-model';
import Overlay from 'ol/Overlay';
import { useTranslation } from 'react-i18next';
import { createDebugLayer } from 'map/layers/debug-layer';
import { createDebug1mPointsLayer } from './layers/debug-1m-points-layer';
import { measurementTool } from 'map/tools/measurement-tool';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { ChangeTimes } from 'common/common-slice';
import { createTrackNumberDiagramLayer } from 'map/layers/highlight/track-number-diagram-layer';
import { LineString, Point as OlPoint } from 'ol/geom';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import useResizeObserver from 'use-resize-observer';
import { createGeometryAlignmentLayer } from 'map/layers/geometry/geometry-alignment-layer';
import { createGeometryKmPostLayer } from 'map/layers/geometry/geometry-km-post-layer';
import { createKmPostLayer } from 'map/layers/km-post/km-post-layer';
import { createAlignmentLinkingLayer } from 'map/layers/alignment-linking-layer';
import { createPlanAreaLayer } from 'map/layers/geometry/plan-area-layer';
import { pointToCoords } from 'map/layers/utils/layer-utils';
import { createGeometrySwitchLayer } from 'map/layers/geometry/geometry-switch-layer';
import { createSwitchLayer } from 'map/layers/switch/switch-layer';
import { createBackgroundMapLayer } from 'map/layers/background-map-layer';
import TileSource from 'ol/source/Tile';
import TileLayer from 'ol/layer/Tile';
import { MapLayer } from 'map/layers/utils/layer-model';
import { filterNotEmpty } from 'utils/array-utils';
import { mapLayerZIndexes } from 'map/layers/utils/layer-visibility-limits';
import { createLocationTrackAlignmentLayer } from 'map/layers/alignment/location-track-alignment-layer';
import { createReferenceLineAlignmentLayer } from 'map/layers/alignment/reference-line-alignment-layer';
import { createLocationTrackBackgroundLayer } from 'map/layers/alignment/location-track-background-layer';
import { createReferenceLineBackgroundLayer } from 'map/layers/alignment/reference-line-background-layer';
import { createReferenceLineBadgeLayer } from 'map/layers/alignment/reference-line-badge-layer';
import { createLocationTrackBadgeLayer } from 'map/layers/alignment/location-track-badge-layer';
import { createDuplicateTracksHighlightLayer } from 'map/layers/highlight/duplicate-tracks-highlight-layer';
import { createMissingLinkingHighlightLayer } from 'map/layers/highlight/missing-linking-highlight-layer';
import { createMissingProfileHighlightLayer } from 'map/layers/highlight/missing-profile-highlight-layer';
import { createTrackNumberEndPointAddressesLayer } from 'map/layers/alignment/track-number-end-point-addresses-layer';
import { Point } from 'model/geometry';
import { createPlanSectionHighlightLayer } from 'map/layers/highlight/plan-section-highlight-layer';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';

declare global {
    interface Window {
        map: OlMap;
    }
}

type MapViewProps = {
    map: Map;
    selection: Selection;
    publishType: PublishType;
    linkingState?: LinkingState;
    onSelect: OnSelectFunction;
    changeTimes: ChangeTimes;
    onHighlightItems: OnHighlightItemsFunction;
    onClickLocation: OnClickLocationFunction;
    onViewportUpdate?: (viewport: MapViewport) => void;
    onShownLayerItemsChange: (items: OptionalShownItems) => void;
    onSetLayoutPoint?: (linkPoint: LinkPoint) => void;
    onsetGeometryPoint?: (linkPoint: LinkPoint) => void;
    onSetLayoutClusterLinkPoint?: (linkPoint: LinkPoint) => void;
    onSetGeometryClusterLinkPoint?: (linkPoint: LinkPoint) => void;
    onRemoveGeometryLinkPoint?: (linkPoint: LinkPoint) => void;
    onRemoveLayoutLinkPoint?: (linkPoint: LinkPoint) => void;
    hoveredOverPlanSection: HighlightedAlignment | undefined;
};

const defaultScaleLine: ScaleLine = new ScaleLine({
    units: 'metric',
    minWidth: 80,
});

function getOlViewByDomainViewport(viewport: MapViewport): OlView {
    return new OlView({
        center: [viewport.center.x, viewport.center.y],
        resolution: viewport.resolution,
        maxZoom: 32,
        minZoom: 6,
        projection: LAYOUT_SRID,
        smoothResolutionConstraint: true,
    });
}

function getDomainViewportByOlView(map: OlMap): MapViewport {
    const view = map.getView();
    const center = view.getCenter() as number[];
    const extent = map.getView().calculateExtent();
    return {
        center: {
            x: center[0],
            y: center[1],
        },
        resolution: view.getResolution() as number,
        area: {
            x: { min: extent[0], max: extent[2] },
            y: { min: extent[1], max: extent[3] },
        },
        source: 'Map',
    };
}

const MapView: React.FC<MapViewProps> = ({
    map,
    selection,
    publishType,
    linkingState,
    changeTimes,
    onSelect,
    onViewportUpdate,
    hoveredOverPlanSection,
    ...props
}: MapViewProps) => {
    const { t } = useTranslation();

    // State to store OpenLayers map object between renders
    const [olMap, setOlMap] = React.useState<OlMap>();
    const olMapContainer = React.useRef<HTMLDivElement>(null);
    const [visibleLayers, setVisibleLayers] = React.useState<MapLayer[]>([]);
    const [measurementToolActive, setMeasurementToolActive] = React.useState(false);
    const [hoveredLocation, setHoveredLocation] = React.useState<Point>();

    const mapLayers = [...map.visibleLayers].sort().join();

    const handleClusterPointClick = (clickType: string) => {
        const clusterPoint = selection.selectedItems.clusterPoints[0];
        if (clusterPoint) {
            switch (clickType) {
                case 'all':
                    props.onSetLayoutClusterLinkPoint &&
                        props.onSetLayoutClusterLinkPoint(clusterPoint.layoutPoint);
                    props.onSetGeometryClusterLinkPoint &&
                        props.onSetGeometryClusterLinkPoint(clusterPoint.geometryPoint);
                    break;
                case 'geometryPoint':
                    props.onSetGeometryClusterLinkPoint &&
                        props.onSetGeometryClusterLinkPoint(clusterPoint.geometryPoint);
                    props.onRemoveLayoutLinkPoint &&
                        props.onRemoveLayoutLinkPoint(clusterPoint.layoutPoint);
                    break;
                case 'layoutPoint':
                    props.onSetLayoutClusterLinkPoint &&
                        props.onSetLayoutClusterLinkPoint(clusterPoint.layoutPoint);
                    props.onRemoveGeometryLinkPoint &&
                        props.onRemoveGeometryLinkPoint(clusterPoint.geometryPoint);
                    break;
                case 'remove':
                    if (props.onRemoveGeometryLinkPoint && props.onRemoveLayoutLinkPoint) {
                        props.onRemoveLayoutLinkPoint(clusterPoint.layoutPoint);
                        props.onRemoveGeometryLinkPoint(clusterPoint.geometryPoint);
                    }
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
        const controls = defaultControls();
        controls.extend([defaultScaleLine]);
        const interactions = defaults();
        //Mouse middle click pan
        interactions.push(new DragPan({ condition: (event) => event.originalEvent.which == 2 }));

        // use in the browser window.map.getPixelFromCoordinate([x,y])
        window.map = new OlMap({
            controls: controls,
            interactions: interactions,
            target: olMapContainer.current as HTMLElement,
            view: getOlViewByDomainViewport(map.viewport),
        });

        setOlMap(window.map);
    }, []);

    // Track map view port changes
    React.useEffect(() => {
        if (!olMap) return;

        const listenerInfo = olMap.on('moveend', () => {
            if (onViewportUpdate) {
                onViewportUpdate(getDomainViewportByOlView(olMap));
            }
        });

        return () => {
            olMap.un('moveend', listenerInfo.listener);
        };
    }, [olMap]);

    React.useEffect(() => {
        const clusterPoint = selection.selectedItems.clusterPoints[0];

        if (!olMap || !clusterPoint) return;
        const pos = pointToCoords(clusterPoint);
        const popupElement = document.getElementById('clusteroverlay') || undefined;
        const popup = new Overlay({
            position: pos,
            offset: [7, 0],
            element: popupElement,
        });
        olMap.addOverlay(popup);
    }, [olMap, selection.selectedItems.clusterPoints[0]]);

    // Update the view"port" of the map
    React.useEffect(() => {
        if (!olMap) {
            return;
        }

        // Ignore viewport changes made by the map itself.
        // Without this check the map can get into invalid state if it is
        // quickly panned (moved) back and forth, and surprisingly this
        // happens quite easily in real life.
        if (map.viewport.source != 'Map') {
            olMap.setView(getOlViewByDomainViewport(map.viewport));
        }
    }, [olMap, map.viewport]);

    // Convert layer domain models into OpenLayers layers
    React.useEffect(() => {
        if (!olMap) return;

        const olView = olMap.getView();
        const resolution = olView.getResolution() || 0;

        // Create OpenLayers objects by domain layers
        const updatedLayers = map.visibleLayers
            .map((layerName) => {
                const mapTiles = calculateMapTiles(olView, undefined);

                // Step 2. create the layer
                // In some cases an adapter wants to reuse existing OL layer,
                // e.g. tile layers cause flickering if recreated every time
                const existingOlLayer = visibleLayers.find((l) => l.name === layerName)?.layer;

                switch (layerName) {
                    case 'background-map-layer':
                        return createBackgroundMapLayer(existingOlLayer as TileLayer<TileSource>);
                    case 'track-number-diagram-layer':
                        return createTrackNumberDiagramLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<LineString>>,
                            changeTimes,
                            publishType,
                            resolution,
                            map.layerSettings['track-number-diagram-layer'],
                        );
                    case 'track-number-addresses-layer':
                        return createTrackNumberEndPointAddressesLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<OlPoint>>,
                            changeTimes,
                            publishType,
                            resolution,
                            map.layerSettings['track-number-diagram-layer'],
                        );
                    case 'reference-line-alignment-layer':
                        return createReferenceLineAlignmentLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<LineString | OlPoint>>,
                            selection,
                            publishType,
                            linkingState,
                            changeTimes,
                            props.onShownLayerItemsChange,
                        );
                    case 'reference-line-background-layer':
                        return createReferenceLineBackgroundLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<LineString>>,
                            publishType,
                            changeTimes,
                        );
                    case 'reference-line-badge-layer':
                        return createReferenceLineBadgeLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<OlPoint>>,
                            selection,
                            publishType,
                            linkingState,
                            changeTimes,
                            resolution,
                        );
                    case 'location-track-alignment-layer':
                        return createLocationTrackAlignmentLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<LineString | OlPoint>>,
                            selection,
                            publishType,
                            linkingState,
                            changeTimes,
                            olView,
                            props.onShownLayerItemsChange,
                        );
                    case 'location-track-background-layer':
                        return createLocationTrackBackgroundLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<LineString>>,
                            publishType,
                            changeTimes,
                            resolution,
                        );
                    case 'location-track-badge-layer':
                        return createLocationTrackBadgeLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<OlPoint>>,
                            selection,
                            publishType,
                            linkingState,
                            changeTimes,
                            resolution,
                        );
                    case 'missing-linking-highlight-layer':
                        return createMissingLinkingHighlightLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<LineString>>,
                            publishType,
                            changeTimes,
                            resolution,
                        );
                    case 'duplicate-tracks-highlight-layer':
                        return createDuplicateTracksHighlightLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<LineString>>,
                            publishType,
                            changeTimes,
                            resolution,
                        );
                    case 'missing-profile-highlight-layer':
                        return createMissingProfileHighlightLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<LineString>>,
                            publishType,
                            changeTimes,
                            resolution,
                        );
                    case 'plan-section-highlight-layer':
                        return createPlanSectionHighlightLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<LineString>>,
                            publishType,
                            changeTimes,
                            resolution,
                            hoveredOverPlanSection,
                        );
                    case 'km-post-layer':
                        return createKmPostLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<OlPoint | Polygon>>,
                            selection,
                            publishType,
                            changeTimes,
                            olView,
                            props.onShownLayerItemsChange,
                        );
                    case 'switch-layer':
                        return createSwitchLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<OlPoint>>,
                            selection,
                            publishType,
                            changeTimes,
                            olView,
                            props.onShownLayerItemsChange,
                        );
                    case 'geometry-alignment-layer':
                        return createGeometryAlignmentLayer(
                            existingOlLayer as VectorLayer<VectorSource<LineString>>,
                            selection,
                            publishType,
                            changeTimes,
                            resolution,
                        );
                    case 'geometry-km-post-layer':
                        return createGeometryKmPostLayer(
                            resolution,
                            existingOlLayer as VectorLayer<VectorSource<OlPoint | Polygon>>,
                            selection,
                            publishType,
                        );
                    case 'geometry-switch-layer':
                        return createGeometrySwitchLayer(
                            existingOlLayer as VectorLayer<VectorSource<OlPoint>>,
                            selection,
                            publishType,
                            resolution,
                        );
                    case 'alignment-linking-layer':
                        return createAlignmentLinkingLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<OlPoint | LineString>>,
                            selection,
                            linkingState,
                            changeTimes,
                            resolution,
                        );
                    case 'switch-linking-layer':
                        return createSwitchLinkingLayer(
                            mapTiles,
                            resolution,
                            existingOlLayer as VectorLayer<VectorSource<OlPoint>>,
                            selection,
                            linkingState as LinkingSwitch,
                        );
                    case 'plan-area-layer':
                        return createPlanAreaLayer(
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<Polygon>>,
                            changeTimes,
                        );
                    case 'debug-1m-points-layer':
                        return createDebug1mPointsLayer(
                            existingOlLayer as VectorLayer<VectorSource<OlPoint>>,
                            selection,
                            publishType,
                            resolution,
                        );
                    case 'debug-layer':
                        return createDebugLayer(
                            existingOlLayer as VectorLayer<VectorSource<OlPoint>>,
                        );
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
        publishType,
        linkingState,
        map.layerSettings,
        hoveredOverPlanSection,
    ]);

    React.useEffect(() => {
        if (!olMap) return;

        // Activate current tool
        const toolActivateOptions: MapToolActivateOptions = {
            onSelect: onSelect,
            onHighlightItems: props.onHighlightItems,
            onHoverLocation: (p) => setHoveredLocation(p),
            onClickLocation: props.onClickLocation,
        };

        const deactivateCallbacks = [
            pointLocationTool.activate(olMap, visibleLayers, toolActivateOptions),
        ];

        if (!measurementToolActive) {
            deactivateCallbacks.push(
                selectTool.activate(olMap, visibleLayers, toolActivateOptions),
            );

            deactivateCallbacks.push(
                highlightTool.activate(olMap, visibleLayers, toolActivateOptions),
            );
        }

        // Return function to clean up initialized stuff
        return () => {
            deactivateCallbacks.forEach((f) => f());
        };
    }, [olMap, visibleLayers, measurementToolActive]);

    React.useEffect(() => {
        if (measurementToolActive && olMap) {
            return measurementTool.activate(olMap);
        }
    }, [olMap, measurementToolActive]);

    return (
        <div className={styles.map}>
            <ol className="map__map-tools">
                <li
                    onClick={() => setMeasurementToolActive(false)}
                    className={createClassName(
                        styles['map__map-tool'],
                        !measurementToolActive && styles['map__map-tool--active'],
                    )}>
                    <Icons.Select color={IconColor.INHERIT} />
                </li>
                <li
                    onClick={() => setMeasurementToolActive(true)}
                    className={createClassName(
                        styles['map__map-tool'],
                        measurementToolActive && styles['map__map-tool--active'],
                    )}>
                    <Icons.Measure color={IconColor.INHERIT} />
                </li>
            </ol>

            <div ref={olMapContainer} className={styles['map__ol-map']} />

            <div id="clusteroverlay">
                {selection.selectedItems.clusterPoints[0] && (
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

            <LocationHolderView hoveredCoordinate={hoveredLocation} />
        </div>
    );
};

export default MapView;
