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
import { clearFeatures } from 'map/layers/utils/layer-utils';

type DebugLayerPoint = {
    x: number;
    y: number;
    color?: string;
    size?: number;
    text?: string;
};

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

function createAddressPointFeatures(data: AlignmentAddresses): Feature<OlPoint>[] {
    const startColor = data.startIntersect == 'WITHIN' ? 'green' : 'red';
    const endColor = data.endIntersect == 'WITHIN' ? 'green' : 'red';
    const debugData = [
        addressPointToDebugData('S', startColor, data.startPoint),
        addressPointToDebugData('E', endColor, data.endPoint),
        ...data.midPoints.map((p, i) => addressPointToDebugData(`M_${i + 1}`, 'blue', p)),
    ];
    return createDebugFeatures(debugData);
}

function createDebugFeature(item: DebugLayerPoint): Feature<OlPoint> {
    const feature = new Feature({
        geometry: new OlPoint([item.x, item.y]),
    });

    const color = item.color || 'blue';
    const size = item.size || 3;

    feature.setStyle(
        new Style({
            image: new Circle({
                radius: size,
                stroke: new Stroke({ color }),
                fill: new Fill({ color }),
            }),
            text: item.text
                ? new Text({
                      text: item.text,
                      scale: 1.5,
                      fill: new Fill({ color }),
                      offsetY: -(size + 15),
                  })
                : undefined,
        }),
    );

    return feature;
}

function createDebugFeatures(points: DebugLayerPoint[]): Feature<OlPoint>[] {
    return points.flatMap((point) => createDebugFeature(point));
}

export function createDebug1mPointsLayer(
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    resolution: number,
): MapLayer {
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    function updateFeatures(features: Feature<OlPoint>[]) {
        clearFeatures(vectorSource);
        vectorSource.addFeatures(features);
    }

    const selected = selection.selectedItems.locationTracks[0];
    if (selected && resolution <= DEBUG_1M_POINTS) {
        getAddressPoints(selected, publishType)
            .then((addresses) =>
                addresses
                    ? updateFeatures(createAddressPointFeatures(addresses))
                    : updateFeatures([]),
            )
            .catch(() => clearFeatures(vectorSource));
    } else {
        updateFeatures([]);
    }

    return {
        name: 'debug-1m-points-layer',
        layer: layer,
    };
}
