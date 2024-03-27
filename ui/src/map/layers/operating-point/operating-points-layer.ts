import { MapLayerName, MapTile } from 'map/map-model';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { Point as OlPoint } from 'ol/geom';
import { MapLayer } from 'map/layers/utils/layer-model';
import { createLayer, loadLayerData, pointToCoords } from 'map/layers/utils/layer-utils';
import { getOperatingPoints } from 'track-layout/layout-operating-point-api';
import Feature from 'ol/Feature';
import { OperatingPoint } from 'track-layout/track-layout-model';
import Style from 'ol/style/Style';
import { Circle, Fill, Stroke, Text } from 'ol/style';
import OlView from 'ol/View';
import { ChangeTimes } from 'common/common-slice';
import { fieldComparator } from 'utils/array-utils';
import * as Limits from 'map/layers/utils/layer-visibility-limits';

const layerName: MapLayerName = 'operating-points-layer';

enum OperatingPointStyle {
    Large,
    Medium,
    Small,
    None,
}

const operatingPointStyleResolutions = [
    {
        style: OperatingPointStyle.Large,
        resolution: Limits.OPERATING_POINTS_LARGE,
    },
    {
        style: OperatingPointStyle.Medium,
        resolution: Limits.OPERATING_POINTS_MEDIUM,
    },
    {
        style: OperatingPointStyle.Small,
        resolution: Limits.OPERATING_POINTS_SMALL,
    },
];

export function createOperatingPointLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    olView: OlView,
    changeTimes: ChangeTimes,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer, true, true);
    const resolution = olView.getResolution() || 0;

    const showOperatingPoints = resolution <= Limits.OPERATING_POINTS_SMALL;

    const getOperatingPointsFromApi = async () => {
        return (
            await Promise.all(
                mapTiles.map((tile) => getOperatingPoints(tile, changeTimes.operatingPoints)),
            )
        ).flat();
    };
    const onLoadingChange = () => {};
    const createFeatures = (points: OperatingPoint[]) =>
        showOperatingPoints ? points.map(renderPoint) : [];
    loadLayerData(source, isLatest, onLoadingChange, getOperatingPointsFromApi(), createFeatures);

    return {
        name: layerName,
        layer: layer,
    };
}

function getOperatingPointStyle(style: OperatingPointStyle, operatingPointName: string) {
    switch (style) {
        case OperatingPointStyle.Small:
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
                    text: operatingPointName,
                    fill: new Fill({ color: 'black' }),
                    offsetX: 10,
                    textAlign: 'left',
                    backgroundFill: new Fill({ color: 'rgba(255, 255, 255, 0.85)' }),
                    scale: 1,
                    font: '11px "Open Sans"',
                }),
            });
        case OperatingPointStyle.Medium:
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
                    text: operatingPointName,
                    fill: new Fill({ color: 'black' }),
                    offsetX: 12,
                    textAlign: 'left',
                    backgroundFill: new Fill({ color: 'rgba(255, 255, 255, 0.85)' }),
                    font: '14px "Open Sans"',
                }),
            });
        case OperatingPointStyle.Large:
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
                    text: operatingPointName,
                    fill: new Fill({ color: 'black' }),
                    offsetX: 14,
                    textAlign: 'left',
                    backgroundFill: new Fill({ color: 'rgba(255, 255, 255, 0.85)' }),
                    font: '16px "Open Sans"',
                }),
            });
        case OperatingPointStyle.None:
            return [];
    }
}

const operatingPointStyleResolutionsSmallestFirst = operatingPointStyleResolutions.sort(
    fieldComparator((a) => a.resolution),
);

function getOperatingPointStyleForFeature(_feature: Feature, resolution: number) {
    const point = _feature.get('operatingPoint') as OperatingPoint;
    const smallestResolutionConf = operatingPointStyleResolutionsSmallestFirst.find(
        (styleResolution) => resolution <= styleResolution.resolution,
    );
    const selectedStyle =
        smallestResolutionConf !== undefined
            ? smallestResolutionConf.style
            : OperatingPointStyle.None;
    return getOperatingPointStyle(selectedStyle, point.name);
}

function renderPoint(point: OperatingPoint): Feature<OlPoint> {
    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(point.location)),
    });
    feature.set('operatingPoint', point);
    feature.setStyle(getOperatingPointStyleForFeature);

    return feature;
}
