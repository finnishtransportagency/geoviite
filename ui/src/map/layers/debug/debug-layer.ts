import Feature from 'ol/Feature';
import { LineString, Point as OlPoint } from 'ol/geom';
import { filterNotEmpty } from 'utils/array-utils';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';
import { MapLayer } from 'map/layers/utils/layer-model';
import {
    clearFeatures,
    createLayer,
    GeoviiteMapLayer,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { MapLayerName } from 'map/map-model';

type DebugLayerFeatureGeometryType = OlPoint | LineString;

type DebugLayerPoint = {
    type: 'point';
    x: number;
    y: number;
    color?: string;
    size?: number;
    text?: string;
};

type DebugLayerLine = {
    type: 'line';
    points: {
        x: number;
        y: number;
    }[];
    color?: string;
    size?: number;
    text?: string;
};

type DebugLayerObject = DebugLayerPoint | DebugLayerLine;
let debugLayerData: DebugLayerObject[] = [];

declare global {
    function setDebugLayerData(data: DebugLayerObject[]): void;
}

let updateLayerFunc: (() => void) | undefined = undefined;

globalThis.setDebugLayerData = (data: DebugLayerObject[]) => {
    debugLayerData = data;
    if (updateLayerFunc) {
        updateLayerFunc();
    }
};

function createDebugFeatures(
    debugDataObjects: DebugLayerObject[],
): Feature<DebugLayerFeatureGeometryType>[] {
    return debugDataObjects
        .flatMap((debugDataObject) => {
            if (debugDataObject.type === 'point') {
                const feature = new Feature({
                    geometry: new OlPoint(pointToCoords(debugDataObject)),
                });

                const color = debugDataObject.color || 'blue';
                const size = debugDataObject.size || 3;

                feature.setStyle(
                    new Style({
                        image: new Circle({
                            radius: size,
                            stroke: new Stroke({ color }),
                            fill: new Fill({ color }),
                        }),
                        text: debugDataObject.text
                            ? new Text({
                                  text: debugDataObject.text,
                                  scale: 1.5,
                                  fill: new Fill({ color }),
                                  offsetY: -(size + 15),
                              })
                            : undefined,
                    }),
                );

                return feature;
            } else if (debugDataObject.type === 'line') {
                const feature = new Feature({
                    geometry: new LineString(
                        debugDataObject.points.map((point) => pointToCoords(point)),
                    ),
                });

                const color = debugDataObject.color || 'blue';
                const size = debugDataObject.size || 3;

                feature.setStyle(
                    new Style({
                        stroke: new Stroke({
                            color: color,
                            width: 2,
                        }),
                        text: debugDataObject.text
                            ? new Text({
                                  text: debugDataObject.text,
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
    existingOlLayer: GeoviiteMapLayer<DebugLayerFeatureGeometryType> | undefined,
): MapLayer {
    const { layer, source } = createLayer(layerName, existingOlLayer);

    function updateFeatures(features: Feature<DebugLayerFeatureGeometryType>[]) {
        clearFeatures(source);
        source.addFeatures(features);
    }

    updateLayerFunc = () => {
        updateFeatures(createDebugFeatures(debugLayerData));
    };

    updateLayerFunc();

    return { name: layerName, layer: layer };
}
