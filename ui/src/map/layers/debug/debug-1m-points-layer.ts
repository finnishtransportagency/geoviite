import Feature from 'ol/Feature';
import { Point as OlPoint } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';
import { AddressPoint, PublishType } from 'common/common-model';
import { AlignmentAddresses, getAddressPoints } from 'common/geocoding-api';
import { DEBUG_1M_POINTS } from '../utils/layer-visibility-limits';
import { MapLayer } from 'map/layers/utils/layer-model';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { first } from 'utils/array-utils';

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

function createDebugFeatures(points: DebugLayerPoint[]): Feature<OlPoint>[] {
    return points.flatMap((point) => {
        const feature = new Feature({
            geometry: new OlPoint(pointToCoords(point)),
        });

        const color = point.color || 'blue';
        const size = point.size || 3;

        feature.setStyle(
            new Style({
                image: new Circle({
                    radius: size,
                    stroke: new Stroke({ color }),
                    fill: new Fill({ color }),
                }),
                text: point.text
                    ? new Text({
                          text: point.text,
                          scale: 1.5,
                          fill: new Fill({ color }),
                          offsetY: -(size + 15),
                      })
                    : undefined,
            }),
        );

        return feature;
    });
}

export function createDebug1mPointsLayer(
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    resolution: number,
): MapLayer {
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    const selected = first(selection.selectedItems.locationTracks);
    let inFlight = false;
    if (selected && resolution <= DEBUG_1M_POINTS) {
        inFlight = true;
        getAddressPoints(selected, publishType)
            .then((addresses) => {
                clearFeatures(vectorSource);
                addresses && vectorSource.addFeatures(createAddressPointFeatures(addresses));
            })
            .catch(() => clearFeatures(vectorSource))
            .finally(() => {
                inFlight = false;
            });
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'debug-1m-points-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
