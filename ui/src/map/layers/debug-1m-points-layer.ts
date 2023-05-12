import Feature from 'ol/Feature';
import OlPoint from 'ol/geom/Point';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Selection } from 'selection/selection-model';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';
import { AddressPoint, PublishType } from 'common/common-model';
import { AlignmentAddresses, getAddressPoints } from 'common/geocoding-api';
import { DEBUG_1M_POINTS } from './utils/layer-visibility-limits';
import { MapLayer } from 'map/layers/utils/layer-model';

export type Debug1mPointsLayerFeatureType = OlPoint;

type DebugLayerPoint = {
    x: number;
    y: number;
    color?: string;
    size?: number;
    text?: string;
};

export type DebugLayerData = DebugLayerPoint[];

function addressPointToDebugData(
    name: string,
    color: string,
    point: AddressPoint,
): DebugLayerPoint {
    const address = `${point.address.kmNumber}+${point.address.meters}`;
    return {
        x: point.point.x,
        y: point.point.y,
        color: color,
        text: `${name}=${address}`,
    };
}

function createAddressPointFeatures(
    data: AlignmentAddresses,
): Feature<Debug1mPointsLayerFeatureType>[] {
    const startColor = data.startIntersect == 'WITHIN' ? 'green' : 'red';
    const endColor = data.endIntersect == 'WITHIN' ? 'green' : 'red';
    const debugData: DebugLayerData = [
        addressPointToDebugData('S', startColor, data.startPoint),
        addressPointToDebugData('E', endColor, data.endPoint),
        ...data.midPoints.map((p, i) => addressPointToDebugData(`M_${i + 1}`, 'blue', p)),
    ];
    return createDebugFeatures(debugData);
}

function createDebugFeature(item: DebugLayerPoint): Feature<Debug1mPointsLayerFeatureType> {
    const feature: Feature<Debug1mPointsLayerFeatureType> = new Feature({
        geometry: new OlPoint([item.x, item.y]),
    });
    const color = item.color || 'blue';
    const size = item.size || 3;
    feature.setStyle(
        new Style({
            image: new Circle({
                radius: size,
                stroke: new Stroke({
                    color: color,
                }),
                fill: new Fill({
                    color: color,
                }),
            }),
            text: item.text
                ? new Text({
                      text: item.text,
                      scale: 1.5,
                      fill: new Fill({
                          color: color,
                      }),
                      offsetY: -(size + 15),
                  })
                : undefined,
        }),
    );
    return feature;
}

function createDebugFeatures(data: DebugLayerData): Feature<Debug1mPointsLayerFeatureType>[] {
    return data.flatMap((item: DebugLayerPoint) => createDebugFeature(item));
}

export function createDebug1mPointsLayer(
    existingOlLayer: VectorLayer<VectorSource<Debug1mPointsLayerFeatureType>> | undefined,
    selection: Selection,
    publishType: PublishType,
    resolution: number | undefined,
): MapLayer {
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    function updateFeatures(features: Feature<Debug1mPointsLayerFeatureType>[]) {
        vectorSource.clear();
        vectorSource.addFeatures(features);
    }

    const selected = selection.selectedItems.locationTracks[0];
    if (selected && resolution && resolution < DEBUG_1M_POINTS) {
        getAddressPoints(selected, publishType).then((data) =>
            data ? updateFeatures(createAddressPointFeatures(data)) : updateFeatures([]),
        );
    } else {
        updateFeatures([]);
    }

    return {
        name: 'debug-1m-points-layer',
        layer: layer,
    };
}
