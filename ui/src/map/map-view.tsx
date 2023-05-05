import BaseLayer from 'ol/layer/Base';
import React from 'react';
import OlMap from 'ol/Map';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnHoverLocationFunction,
    OnSelectFunction,
    Selection,
} from 'selection/selection-model';
import { defaults } from 'ol/interaction';
import DragPan from 'ol/interaction/DragPan.js';
import { OlLayerAdapter } from 'map/layers/layer-model';
import 'ol/ol.css';
import OlView from 'ol/View';
import { LayoutAlignmentsLayer, Map, MapViewport, OptionalShownItems } from 'map/map-model';
import './layers/tile-layer'; // Load module to initialize adapter
import './layers/alignment-layer'; // Load module to initialize adapter
import './layers/geometry-layer'; // Load module to initialize adapter
import './layers/km-post-layer'; // Load module to initialize adapter
import './layers/switch-layer'; // Load module to initialize adapter
import './layers/plan-area-layer'; // Load module to initialize adapter
import './layers/linking-layer'; // Load module to initialize adapter
import {
    createSwitchLinkingLayerAdapter,
    SwitchLinkingFeatureType,
} from './layers/switch-linking-layer'; // Load module to initialize adapter
import styles from './map.module.scss';
import { selectTool } from './tools/select-tool';
import { MapToolActivateOptions } from './tools/tool-model';
import { calculateMapTiles, calculateTileSize } from 'map/map-utils';
import { adapterInfoRegister } from 'map/layers/register';
import { defaults as defaultControls, ScaleLine } from 'ol/control';
import { highlightTool } from 'map/tools/highlight-tool';
import { fromExtent } from 'ol/geom/Polygon';
import { searchShownItemsFromLayers } from 'map/tools/tool-utils';
import { LinkingState, LinkingSwitch, LinkPoint } from 'linking/linking-model';
import { pointLocationTool } from 'map/tools/point-location-tool';
import { LocationHolderView } from 'map/location-holder/location-holder-view';
import {
    createManualSwitchLinkingLayerAdapter,
    ManualSwitchLinkingLayerFeatureType,
} from 'map/layers/manual-switch-linking-layer';
import { LAYOUT_SRID } from 'track-layout/track-layout-model';
import { PublishType } from 'common/common-model';
import Overlay from 'ol/Overlay';
import { useTranslation } from 'react-i18next';
import { createDebugLayerAdapter, DebugLayerFeatureType } from 'map/layers/debug-layer';
import {
    createDebug1mPointsLayerAdapter,
    Debug1mPointsLayerFeatureType,
} from './layers/debug-1m-points-layer';
import { measurementTool } from 'map/tools/measurement-tool';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { ChangeTimes } from 'common/common-slice';
import { createTrackNumberDiagramLayerAdapter } from 'map/layers/track-number-diagram-layer';
import { LineString } from 'ol/geom';
import { createAlignmentLayerAdapter } from 'map/layers/alignment-layer';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';

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
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
    onViewportUpdate?: (viewport: MapViewport) => void;
    onShownLayerItemsChange?: (items: OptionalShownItems) => void;
    onSetLayoutPoint?: (linkPoint: LinkPoint) => void;
    onsetGeometryPoint?: (linkPoint: LinkPoint) => void;
    onSetLayoutClusterLinkPoint?: (linkPoint: LinkPoint) => void;
    onSetGeometryClusterLinkPoint?: (linkPoint: LinkPoint) => void;
    onRemoveGeometryLinkPoint?: (linkPoint: LinkPoint) => void;
    onRemoveLayoutLinkPoint?: (linkPoint: LinkPoint) => void;
};

const defaultScaleLine: ScaleLine = new ScaleLine({
    units: 'metric',
    minWidth: 80,
});

/**
 * Returns layers of OL map in an object structure using layer names as keys
 * and layers as values, like this:
 *
 * {
 *  "layer name": layerObject
 * }
 */
function getLayersByName(olMap: OlMap) {
    return olMap
        .getLayers()
        .getArray()
        .reduce<{ [key: string]: BaseLayer }>((hash, layer) => {
            hash[layer.get('name') as string] = layer;
            return hash;
        }, {});
}

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
    ...props
}: MapViewProps) => {
    const { t } = useTranslation();

    // State to store OpenLayers map object between renders
    const [olMap, setOlMap] = React.useState<OlMap | null>(null);
    const olMapContainer = React.useRef<HTMLDivElement>(null);
    const [layerAdapters, setLayerAdapters] = React.useState<OlLayerAdapter[]>([]);
    const [measurementToolActive, setMeasurementToolActive] = React.useState(false);

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

    React.useEffect(() => {
        olMap?.updateSize();
    }, [olMapContainer.current?.clientWidth]);

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
        const pos = [clusterPoint.x, clusterPoint.y];
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

        if (props.onShownLayerItemsChange) {
            // Get items from whole visible map
            const area = olMap.getView().calculateExtent();
            const pol = fromExtent(area);
            const items = searchShownItemsFromLayers(pol, layerAdapters, {});
            props.onShownLayerItemsChange(items);
        }
    }, [olMap, map.viewport, layerAdapters]);

    // Convert layer domain models into OpenLayers layers
    React.useEffect(() => {
        if (!olMap) return;

        const existingOlLayersByName = getLayersByName(olMap);
        const olView = olMap.getView();

        // Create OpenLayers objects by domain layers
        const layerAdapters = map.mapLayers
            .filter((mapLayer) => mapLayer.visible)
            .map((mapLayer) => {
                const adapterInfo = adapterInfoRegister.get(mapLayer);

                // Step 1. calculate tiles for the layer
                let tileSize = adapterInfo?.mapTileSizePx;
                switch (mapLayer.type) {
                    case 'switchLinking':
                        tileSize = calculateTileSize(2);
                        break;
                }
                const mapTiles = calculateMapTiles(olView, tileSize);

                // Step 2. create the layer
                // In some cases an adapter wants to reuse existing OL layer,
                // e.g. tile layers cause flickering if recreated every time
                const existingOlLayer = existingOlLayersByName[mapLayer.id];
                let layerAdapter;
                switch (mapLayer.type) {
                    case 'trackNumberDiagram': {
                        const alignmentLayer = map.mapLayers.find(
                            (l) => l.type === 'alignment',
                        ) as LayoutAlignmentsLayer;

                        layerAdapter = createTrackNumberDiagramLayerAdapter(
                            mapLayer,
                            mapTiles,
                            existingOlLayer as VectorLayer<VectorSource<LineString>>,
                            olView,
                            changeTimes,
                            publishType,
                            alignmentLayer.showReferenceLines,
                        );

                        break;
                    }
                    case 'alignment':
                        layerAdapter = createAlignmentLayerAdapter(
                            mapTiles,
                            existingOlLayer as never,
                            mapLayer,
                            selection,
                            publishType,
                            linkingState,
                            changeTimes,
                            olView,
                            props.onShownLayerItemsChange,
                        );
                        break;
                    case 'switchLinking':
                        layerAdapter = createSwitchLinkingLayerAdapter(
                            mapLayer,
                            mapTiles,
                            olView.getResolution(),
                            existingOlLayer as VectorLayer<VectorSource<SwitchLinkingFeatureType>>,
                            selection,
                            linkingState as LinkingSwitch,
                        );
                        break;
                    case 'manualSwitchLinking':
                        layerAdapter = createManualSwitchLinkingLayerAdapter(
                            mapLayer,
                            mapTiles,
                            olView.getResolution(),
                            existingOlLayer as VectorLayer<
                                VectorSource<ManualSwitchLinkingLayerFeatureType>
                            >,
                            selection,
                            publishType,
                        );
                        break;
                    case 'debug1mPoints':
                        layerAdapter = createDebug1mPointsLayerAdapter(
                            mapLayer,
                            existingOlLayer as VectorLayer<
                                VectorSource<Debug1mPointsLayerFeatureType>
                            >,
                            selection,
                            publishType,
                            olView.getResolution(),
                        );
                        break;
                    case 'debug':
                        layerAdapter = createDebugLayerAdapter(
                            mapLayer,
                            existingOlLayer as VectorLayer<VectorSource<DebugLayerFeatureType>>,
                        );
                        break;
                    default:
                        if (!adapterInfo) {
                            throw new Error(
                                `Cannot create ${mapLayer.type} layer, adapter is not defined!`,
                            );
                        }

                        layerAdapter = adapterInfo.createAdapter(
                            mapTiles,
                            existingOlLayer,
                            mapLayer,
                            selection,
                            publishType,
                            linkingState,
                            changeTimes,
                            olView,
                            props.onShownLayerItemsChange,
                        );
                }

                // Name OL layer so that we can find it later
                layerAdapter.layer.set('name', mapLayer.id);
                return layerAdapter;
            });
        setLayerAdapters(layerAdapters);

        // Set converted layers into map object
        const olLayers = layerAdapters.map((layerAdapter) => layerAdapter.layer);
        olMap.setLayers(olLayers);

        // Activate current tool
        const toolActivateOptions: MapToolActivateOptions = {
            onSelect: onSelect,
            onHighlightItems: props.onHighlightItems,
            onHoverLocation: props.onHoverLocation,
            onClickLocation: props.onClickLocation,
        };

        const deactivateCallbacks = [
            pointLocationTool.activate(olMap, layerAdapters, toolActivateOptions),
        ];

        if (!measurementToolActive) {
            deactivateCallbacks.push(
                selectTool.activate(olMap, layerAdapters, toolActivateOptions),
            );

            deactivateCallbacks.push(
                highlightTool.activate(olMap, layerAdapters, toolActivateOptions),
            );
        }

        // Return function to clean up initialized stuff
        return () => {
            deactivateCallbacks.forEach((f) => f());
        };
    }, [
        olMap,
        map.viewport,
        map.mapLayers,
        map.settingsVisible,
        selection,
        changeTimes,
        publishType,
        linkingState,
        measurementToolActive,
    ]);

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

            <LocationHolderView hoveredCoordinate={map.hoveredLocation} />
        </div>
    );
};

export default MapView;
