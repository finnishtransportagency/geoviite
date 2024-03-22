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
        resolution: 0,
    },
    {
        style: OperatingPointStyle.Medium,
        resolution: 20,
    },
    {
        style: OperatingPointStyle.Small,
        resolution: 75,
    },
    {
        style: OperatingPointStyle.None,
        resolution: 500,
    },
];

export function createOperatingPointLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    olView: OlView,
    changeTimes: ChangeTimes,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);
    const resolution = olView.getResolution() || 0;

    const resolutionLimit = operatingPointStyleResolutions.find(
        (styleResolution) => styleResolution.style == OperatingPointStyle.None,
    );
    if (resolutionLimit === undefined) {
        throw new Error('Resolution limit is not set');
    }

    const getOperatingPointsFromApi = async () => {
        return (
            await Promise.all(
                mapTiles.map((tile) => getOperatingPoints(tile, changeTimes.operatingPoints)),
            )
        ).flat();
    };
    const onLoadingChange = () => {};
    const createFeatures = (points: OperatingPoint[]) =>
        resolution > resolutionLimit.resolution ? [] : points.map(renderPoint);
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
                    font: '18px "Open Sans"',
                }),
            });
        case OperatingPointStyle.None:
            return [];
    }
}

function renderPoint(point: OperatingPoint): Feature<OlPoint> {
    function getStyle(_feature: Feature, resolution: number) {
        const operatingPointStyleResolutionsLargestFirst = operatingPointStyleResolutions.sort(
            fieldComparator((a) => -a.resolution),
        );
        const selectedStyle = operatingPointStyleResolutionsLargestFirst.find(
            (styleResolution) => resolution >= styleResolution.resolution,
        );
        if (selectedStyle === undefined) {
            throw new Error('No style defined for given resolution: ' + resolution);
        }
        return getOperatingPointStyle(selectedStyle.style, point.name);
    }

    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(point.location)),
    });
    feature.setStyle(getStyle);

    return feature;
}
