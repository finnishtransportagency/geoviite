import Feature from 'ol/Feature';
import { Point as OlPoint, Polygon } from 'ol/geom';
import { filterNotEmpty } from 'utils/array-utils';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';
import { MapLayer } from 'map/layers/utils/layer-model';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import liikennepaikat from 'liikennepaikat.json';

type DebugLayerPoint = {
    type: 'point';
    x: number;
    y: number;
    color?: string;
    size?: number;
    text?: string;
};

type DebugLayerPolygon = {
    type: 'polygon';
    coordinates: number[][][];
};

type DebugLayerData = (DebugLayerPolygon | DebugLayerPoint)[];

let debugLayerData: DebugLayerData = [
    ...liikennepaikat.features.map((feat) => {
        const p: DebugLayerPoint = {
            type: 'point',
            x: feat.properties.virallinenSijainti[0],
            y: feat.properties.virallinenSijainti[1],
            text: feat.properties.nimi,
        };
        return p;
    }),
    ...liikennepaikat.features.map((feat) => {
        const coords: number[][][] = feat.geometry.geometries[1]
            .coordinates as unknown as number[][][];
        const p: DebugLayerPolygon = {
            type: 'polygon',
            coordinates: coords,
        };
        return p;
    }),
];

declare global {
    function setDebugLayerData(data: DebugLayerData): void;
}

let updateLayerFunc: (() => void) | undefined = undefined;

globalThis.setDebugLayerData = (data: DebugLayerData) => {
    debugLayerData = data;
    if (updateLayerFunc) {
        updateLayerFunc();
    }
};

type DebugFeatureType = OlPoint | Polygon;

function createDebugFeatures(points: DebugLayerData): Feature<DebugFeatureType>[] {
    return points
        .flatMap((point) => {
            if (point.type == 'polygon') {
                const feature = new Feature({
                    geometry: new Polygon(point.coordinates),
                });
                feature.setStyle(
                    new Style({
                        stroke: new Stroke({
                            color: 'blue',
                            width: 3,
                        }),
                        fill: new Fill({
                            color: 'rgba(0, 0, 255, 0.1)',
                        }),
                    }),
                );
                return feature;
            }
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
            }
        })
        .filter(filterNotEmpty);
}

export function createDebugLayer(
    existingOlLayer: VectorLayer<VectorSource<DebugFeatureType>> | undefined,
): MapLayer {
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    function updateFeatures(features: Feature<DebugFeatureType>[]) {
        clearFeatures(vectorSource);
        vectorSource.addFeatures(features);
    }

    updateLayerFunc = () => {
        updateFeatures(createDebugFeatures(debugLayerData));
    };

    updateLayerFunc();

    return {
        name: 'debug-layer',
        layer: layer,
        requestInFlight: () => false,
    };
}
