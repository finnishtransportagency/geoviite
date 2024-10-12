import Feature from 'ol/Feature';
import { Point as OlPoint } from 'ol/geom';
import { filterNotEmpty } from 'utils/array-utils';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';
import { MapLayer } from 'map/layers/utils/layer-model';
import { clearFeatures, createLayer, pointToCoords } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import { MapLayerName } from 'map/map-model';

type DebugLayerPoint = {
    type: 'point';
    x: number;
    y: number;
    color?: string;
    size?: number;
    text?: string;
};

let debugLayerData: DebugLayerPoint[] = [];

declare global {
    function setDebugLayerData(data: DebugLayerPoint[]): void;
}

let updateLayerFunc: (() => void) | undefined = undefined;

globalThis.setDebugLayerData = (data: DebugLayerPoint[]) => {
    debugLayerData = data;
    if (updateLayerFunc) {
        updateLayerFunc();
    }
};

function createDebugFeatures(points: DebugLayerPoint[]): Feature<OlPoint>[] {
    return points
        .flatMap((point) => {
            if (point.type == 'point') {
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
            } else {
                return undefined;
            }
        })
        .filter(filterNotEmpty);
}

const layerName: MapLayerName = 'debug-layer';

export function createDebugLayer(
    existingOlLayer: VectorLayer<Feature<OlPoint>> | undefined,
): MapLayer {
    const { layer, source } = createLayer(layerName, existingOlLayer);

    function updateFeatures(features: Feature<OlPoint>[]) {
        clearFeatures(source);
        source.addFeatures(features);
    }

    updateLayerFunc = () => {
        updateFeatures(createDebugFeatures(debugLayerData));
    };

    updateLayerFunc();

    return { name: layerName, layer: layer };
}
