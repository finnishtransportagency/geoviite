import { Feature } from 'ol';
import { Polygon as OlPolygon } from 'ol/geom';
import Style from 'ol/style/Style';
import CircleStyle from 'ol/style/Circle.js';
import Fill from 'ol/style/Fill';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { coordsToPolygon, Polygon, Rectangle } from 'model/geometry';
import {
    findMatchingOperationalPoints,
    operationalPointAreaPolygonStyle,
} from 'map/layers/operational-point/operational-points-layer-utils';
import {
    createLayer,
    GeoviiteMapLayer,
    getFeatureCoords,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { PlacingOperationalPointArea } from 'linking/linking-model';
import { getOperationalPoint } from 'track-layout/layout-operational-point-api';
import { LayoutContext } from 'common/common-model';
import { Modify } from 'ol/interaction';
import { doubleClick } from 'ol/events/condition';
import OlMap from 'ol/Map';

const LAYER_NAME = 'operational-points-area-placing-layer';
let modify: Modify | undefined = undefined;

export const createOperationalPointsAreaPlacingLayer = (
    existingOlLayer: GeoviiteMapLayer<OlPolygon>,
    linkingState: PlacingOperationalPointArea | undefined,
    layoutContext: LayoutContext,
    map: OlMap,
    onSetOperationalPointPolygon: (polygon: Polygon) => void,
    onLoadingData: (loading: boolean) => void,
): MapLayer => {
    const { layer, source, isLatest } = createLayer(LAYER_NAME, existingOlLayer, true);
    const selectedOperationalPointId = linkingState?.operationalPoint?.id;

    const previousModify = modify;
    if (previousModify) {
        map.removeInteraction(previousModify);
    }

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

    loadLayerData(
        source,
        isLatest,
        onLoadingData,
        selectedOperationalPointId
            ? getOperationalPoint(selectedOperationalPointId, layoutContext)
            : Promise.resolve(undefined),
        () => {
            const coords = linkingState?.area?.points?.map(pointToCoords);
            if (!coords) return [];

            const feature = new Feature({
                geometry: new OlPolygon([coords]),
            });
            feature.setStyle(operationalPointAreaPolygonStyle(false));
            return [feature];
        },
    );

    return {
        name: LAYER_NAME,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => ({
            operationalPoints: findMatchingOperationalPoints(hitArea, source, options).map(
                (operationalPoint) => operationalPoint.id,
            ),
        }),
        onRemove: () => {
            if (modify) map.removeInteraction(modify);
        },
    };
};
