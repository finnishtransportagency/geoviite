import { MapLayerName, MapTile } from 'map/map-model';
import { Point as OlPoint } from 'ol/geom';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { getOperationalPoints } from 'track-layout/layout-operational-point-api';
import Feature, { FeatureLike } from 'ol/Feature';
import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import Style from 'ol/style/Style';
import { Circle, Fill, Stroke, Text } from 'ol/style';
import OlView from 'ol/View';
import { ChangeTimes } from 'common/common-slice';
import { filterNotEmpty } from 'utils/array-utils';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { LayoutContext } from 'common/common-model';
import { Selection } from 'selection/selection-model';
import mapStyles from 'map/map.module.scss';
import { Rectangle } from 'model/geometry';
import {
    featureStyleFont,
    featureStyleOffsetX,
    featureStyleRadius,
    findMatchingOperationalPoints,
    OPERATIONAL_POINT_FEATURE_DATA_PROPERTY,
    OperationalPointFeatureSize,
    operationalPointStyleResolutionsSmallestFirst,
} from 'map/layers/operational-point/operational-points-layer-utils';

const LAYER_NAME: MapLayerName = 'operational-points-layer';

export function createOperationalPointLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    olView: OlView,
    selection: Selection,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): MapLayer {
    const { layer, source, isLatest } = createLayer(LAYER_NAME, existingOlLayer, true, true);
    const resolution = olView.getResolution() || 0;

    const showOperationalPoints = resolution <= Limits.OPERATIONAL_POINTS_SMALL;

    const getOperationalPointsFromApi = async () => {
        return (
            await Promise.all(
                mapTiles.map((tile) =>
                    getOperationalPoints(tile, layoutContext, changeTimes.operationalPoints),
                ),
            )
        ).flat();
    };
    const onLoadingChange = () => {};
    const createFeatures = (points: OperationalPoint[]) =>
        showOperationalPoints
            ? points
                  .map((point) =>
                      renderOperationalPointFeature(
                          point,
                          selection.selectedItems.operationalPoints,
                          selection.highlightedItems.operationalPoints,
                      ),
                  )
                  .filter(filterNotEmpty)
            : [];
    loadLayerData(source, isLatest, onLoadingChange, getOperationalPointsFromApi(), createFeatures);

    return {
        name: LAYER_NAME,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => ({
            operationalPoints: findMatchingOperationalPoints(hitArea, source, options).map(
                (operationalPoint) => operationalPoint.id,
            ),
        }),
    };
}

const createOperationalPointStyle = (
    operationalPointName: string,
    isSelectedOrHighlighted: boolean,
    size: OperationalPointFeatureSize,
): Style => {
    const color = isSelectedOrHighlighted
        ? mapStyles.selectedOrHighlightedOperationalPointColor
        : mapStyles.operationalPointColor;

    return new Style({
        image: new Circle({
            radius: featureStyleRadius(size),
            stroke: new Stroke({ color: 'white', width: 2 }),
            fill: new Fill({ color }),
        }),
        fill: new Fill({
            color: 'white',
        }),
        text: new Text({
            text: operationalPointName,
            fill: new Fill({ color }),
            textAlign: 'left',
            backgroundFill: new Fill({ color: 'rgba(255, 255, 255, 0.85)' }),
            scale: 1,
            offsetX: featureStyleOffsetX(size),
            font: featureStyleFont(size),
        }),
    });
};

function getOperationalPointStyleForFeature(
    feature: FeatureLike,
    resolution: number,
    selectedOperationalPoints: OperationalPointId[],
    highlightedOperationalPoints: OperationalPointId[],
): Style | undefined {
    const point = feature.get(OPERATIONAL_POINT_FEATURE_DATA_PROPERTY) as OperationalPoint;
    const smallestResolutionConf = operationalPointStyleResolutionsSmallestFirst.find(
        (styleResolution) => resolution <= styleResolution.resolutionUpperLimit,
    );

    return smallestResolutionConf
        ? createOperationalPointStyle(
              point.name,
              selectedOperationalPoints.includes(point.id) ||
                  highlightedOperationalPoints.includes(point.id),
              smallestResolutionConf.style,
          )
        : undefined;
}

function renderOperationalPointFeature(
    point: OperationalPoint,
    selectedOperationalPoints: OperationalPointId[],
    highlightedOperationalPoints: OperationalPointId[],
): Feature<OlPoint> | undefined {
    if (!point.location) {
        return undefined;
    }

    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(point.location)),
    });
    feature.set(OPERATIONAL_POINT_FEATURE_DATA_PROPERTY, point);
    feature.setStyle((feature, resolution) =>
        getOperationalPointStyleForFeature(
            feature,
            resolution,
            selectedOperationalPoints,
            highlightedOperationalPoints,
        ),
    );

    return feature;
}
