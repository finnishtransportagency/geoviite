import { MapLayerName, MapTile } from 'map/map-model';
import { Point as OlPoint } from 'ol/geom';
import { MapLayer } from 'map/layers/utils/layer-model';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { getOperationalPoints } from 'track-layout/layout-operational-point-api';
import Feature from 'ol/Feature';
import { OperationalPoint } from 'track-layout/track-layout-model';
import Style from 'ol/style/Style';
import { Circle, Fill, Stroke, Text } from 'ol/style';
import OlView from 'ol/View';
import { ChangeTimes } from 'common/common-slice';
import { fieldComparator, filterNotEmpty } from 'utils/array-utils';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { LayoutContext } from 'common/common-model';

const layerName: MapLayerName = 'operational-points-layer';

enum OperationalPointStyle {
    Large,
    Medium,
    Small,
    None,
}

const operationalPointStyleResolutions = [
    {
        style: OperationalPointStyle.Large,
        resolutionUpperLimit: Limits.OPERATIONAL_POINTS_LARGE,
    },
    {
        style: OperationalPointStyle.Medium,
        resolutionUpperLimit: Limits.OPERATIONAL_POINTS_MEDIUM,
    },
    {
        style: OperationalPointStyle.Small,
        resolutionUpperLimit: Limits.OPERATIONAL_POINTS_SMALL,
    },
];

export function createOperationalPointLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    olView: OlView,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer, true, true);
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
        showOperationalPoints ? points.map(renderPoint).filter(filterNotEmpty) : [];
    loadLayerData(source, isLatest, onLoadingChange, getOperationalPointsFromApi(), createFeatures);

    return {
        name: layerName,
        layer: layer,
    };
}

function getOperationalPointStyle(style: OperationalPointStyle, operationalPointName: string) {
    switch (style) {
        case OperationalPointStyle.Small:
            return new Style({
                image: new Circle({
                    radius: 4,
                    stroke: new Stroke({ color: 'white', width: 2 }),
                    fill: new Fill({ color: 'black' }),
                }),
                fill: new Fill({
                    color: 'white',
                }),
                text: new Text({
                    text: operationalPointName,
                    fill: new Fill({ color: 'black' }),
                    offsetX: 10,
                    textAlign: 'left',
                    backgroundFill: new Fill({ color: 'rgba(255, 255, 255, 0.85)' }),
                    scale: 1,
                    font: '11px "Open Sans"',
                }),
            });
        case OperationalPointStyle.Medium:
            return new Style({
                image: new Circle({
                    radius: 5,
                    stroke: new Stroke({ color: 'white', width: 2 }),
                    fill: new Fill({ color: 'black' }),
                }),
                fill: new Fill({
                    color: 'white',
                }),
                text: new Text({
                    text: operationalPointName,
                    fill: new Fill({ color: 'black' }),
                    offsetX: 12,
                    textAlign: 'left',
                    backgroundFill: new Fill({ color: 'rgba(255, 255, 255, 0.85)' }),
                    font: '14px "Open Sans"',
                }),
            });
        case OperationalPointStyle.Large:
            return new Style({
                image: new Circle({
                    radius: 6,
                    stroke: new Stroke({ color: 'white', width: 2 }),
                    fill: new Fill({ color: 'black' }),
                }),
                fill: new Fill({
                    color: 'white',
                }),
                text: new Text({
                    text: operationalPointName,
                    fill: new Fill({ color: 'black' }),
                    offsetX: 14,
                    textAlign: 'left',
                    backgroundFill: new Fill({ color: 'rgba(255, 255, 255, 0.85)' }),
                    font: '16px "Open Sans"',
                }),
            });
        case OperationalPointStyle.None:
            return [];
    }
}

const operationalPointStyleResolutionsSmallestFirst = operationalPointStyleResolutions.sort(
    fieldComparator((a) => a.resolutionUpperLimit),
);

function getOperationalPointStyleForFeature(feature: Feature, resolution: number) {
    const point = feature.get('operationalPoint') as OperationalPoint;
    const smallestResolutionConf = operationalPointStyleResolutionsSmallestFirst.find(
        (styleResolution) => resolution <= styleResolution.resolutionUpperLimit,
    );
    const selectedStyle =
        smallestResolutionConf !== undefined
            ? smallestResolutionConf.style
            : OperationalPointStyle.None;
    return getOperationalPointStyle(selectedStyle, point.name);
}

function renderPoint(point: OperationalPoint): Feature<OlPoint> | undefined {
    if (!point.location) {
        return undefined;
    }

    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(point.location)),
    });
    feature.set('operationalPoint', point);
    feature.setStyle(getOperationalPointStyleForFeature);

    return feature;
}
