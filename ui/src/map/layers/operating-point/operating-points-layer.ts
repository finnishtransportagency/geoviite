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

const layerName: MapLayerName = 'operating-points-layer';

const MAX_VISIBLE_RESOLUTION = 500;

export function createOperatingPointLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    olView: OlView,
    changeTimes: ChangeTimes,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);
    const resolution = olView.getResolution() || 0;

    const getOperatingPointsFromApi = async () => {
        return (
            await Promise.all(
                mapTiles.map((tile) => getOperatingPoints(tile, changeTimes.operatingPoints)),
            )
        ).flat();
    };
    const onLoadingChange = () => {};
    const createFeatures = (points: OperatingPoint[]) =>
        resolution > MAX_VISIBLE_RESOLUTION ? [] : points.map(renderPoint);
    loadLayerData(source, isLatest, onLoadingChange, getOperatingPointsFromApi(), createFeatures);

    return {
        name: layerName,
        layer: layer,
    };
}

function renderPoint(point: OperatingPoint): Feature<OlPoint> {
    const style = new Style({
        image: new Circle({
            radius: 8,
            stroke: new Stroke({ color: 'white', width: 2 }),
            fill: new Fill({ color: 'black' }),
        }),
        fill: new Fill({
            color: 'white',
        }),
        text: new Text({
            text: point.name,
            fill: new Fill({ color: 'black' }),
            offsetX: 10,
            textAlign: 'left',
            backgroundFill: new Fill({ color: 'rgba(255, 255, 255, 0.85)' }),
        }),
    });
    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(point.location)),
    });
    feature.setStyle(style);
    return feature;
}
