import { Feature } from 'ol';
import { Polygon as OlPolygon } from 'ol/geom';
import Style from 'ol/style/Style';
import CircleStyle from 'ol/style/Circle.js';
import Fill from 'ol/style/Fill';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { coordsToPolygon, Polygon, Rectangle } from 'model/geometry';
import {
    findMatchingOperationalPoints,
    renderOperationalPointAreaFeature,
} from 'map/layers/operational-point/operational-points-layer-utils';
import {
    createLayer,
    GeoviiteMapLayer,
    getFeatureCoords,
    loadLayerData,
} from 'map/layers/utils/layer-utils';
import { PlacingOperationalPointArea } from 'linking/linking-model';
import { getOperationalPoint } from 'track-layout/layout-operational-point-api';
import { LayoutContext } from 'common/common-model';
import { Modify } from 'ol/interaction';
import { doubleClick } from 'ol/events/condition';
import OlMap from 'ol/Map';

const LAYER_NAME = 'operational-points-area-placing-layer';

export type OperationalPointsAreaPlacingLayer = MapLayer & {
    layer: GeoviiteMapLayer<OlPolygon>;
    modifyInteraction: Modify | undefined;
};

export const createOperationalPointsAreaPlacingLayer = (
    existingLayer: OperationalPointsAreaPlacingLayer | undefined,
    linkingState: PlacingOperationalPointArea | undefined,
    layoutContext: LayoutContext,
    map: OlMap,
    onSetOperationalPointPolygon: (polygon: Polygon) => void,
    onLoadingData: (loading: boolean) => void,
): OperationalPointsAreaPlacingLayer => {
    const existingOlLayer = existingLayer?.layer;
    const { layer, source, isLatest } = createLayer<OlPolygon>(LAYER_NAME, existingOlLayer, true);
    const selectedOperationalPointId = linkingState?.operationalPoint?.id;

    function setLayerFeatures(createFeatures: () => Feature<OlPolygon>[]) {
        loadLayerData(
            source,
            isLatest,
            onLoadingData,
            selectedOperationalPointId
                ? getOperationalPoint(selectedOperationalPointId, layoutContext)
                : Promise.resolve(undefined),
            createFeatures,
        );
    }

    let modify: Modify | undefined;

    if (linkingState?.area === undefined) {
        if (existingLayer?.modifyInteraction) {
            // react to area being cleared
            map.removeInteraction(existingLayer.modifyInteraction);
            modify = undefined;
            setLayerFeatures(() => []);
        }
    } else {
        if (existingLayer?.modifyInteraction) {
            modify = existingLayer.modifyInteraction;
        } else {
            modify = new Modify({
                source: source,
                deleteCondition: doubleClick,
                style: new Style({
                    image: new CircleStyle({
                        radius: 5,
                        fill: new Fill({
                            color: '#009BFF',
                        }),
                    }),
                }),
            });
            modify.on('modifyend', (event) => {
                const feature = event.features.item(0) as Feature<OlPolygon>;
                if (!feature) {
                    return;
                }

                onSetOperationalPointPolygon(coordsToPolygon(getFeatureCoords(feature)));
            });
            map.addInteraction(modify);
            // these features become the Modify interaction's responsibility after being first drawn, hence we don't
            // want to redraw them
            setLayerFeatures(() => {
                return linkingState?.area
                    ? [
                          renderOperationalPointAreaFeature(
                              linkingState?.area,
                              'SELECTED',
                              'MODIFYING',
                          ),
                      ]
                    : [];
            });
        }
    }

    return {
        name: LAYER_NAME,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => ({
            operationalPoints: findMatchingOperationalPoints(hitArea, source, options).map(
                (operationalPoint) => operationalPoint.id,
            ),
        }),
        modifyInteraction: modify,
        onRemove: () => {
            modify && map.removeInteraction(modify);
        },
    };
};
