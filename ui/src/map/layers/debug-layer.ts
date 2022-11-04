import Feature from 'ol/Feature';
import { Polygon } from 'ol/geom';
import OlPoint from 'ol/geom/Point';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { DebugLayer } from 'map/map-model';
import { LayerItemSearchResult, OlLayerAdapter, SearchItemsOptions } from 'map/layers/layer-model';
import { filterNotEmpty } from 'utils/array-utils';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';

export type DebugLayerFeatureType = OlPoint | Polygon;

type DebugLayerPoint = {
    type: 'point';
    x: number;
    y: number;
    color?: string;
    size?: number;
    text?: string;
};

export type DebugLayerData = DebugLayerPoint[];

let debugLayerData: DebugLayerData = [];
/* .map((p: DebugLayerPoint, i: number) => ({
    ...p,
    text: `${i}`,
})); */

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

function createDebugFeatures(data: DebugLayerData): Feature<DebugLayerFeatureType>[] {
    return data
        .flatMap((item) => {
            let feature: Feature<DebugLayerFeatureType> | null = null;
            if (item.type == 'point') {
                feature = new Feature({
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
            }
            return feature;
        })
        .filter(filterNotEmpty);
}

export function createDebugLayerAdapter(
    mapLayer: DebugLayer,
    existingOlLayer: VectorLayer<VectorSource<DebugLayerFeatureType>> | undefined,
): OlLayerAdapter {
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
    const layer: VectorLayer<VectorSource<DebugLayerFeatureType>> =
        existingOlLayer ||
        new VectorLayer({
            source: vectorSource,
        });

    function clearFeatures() {
        vectorSource.clear();
    }

    function updateFeatures(features: Feature<DebugLayerFeatureType>[]) {
        clearFeatures();
        vectorSource.addFeatures(features);
    }

    layer.setVisible(mapLayer.visible);
    updateFeatures(createDebugFeatures(debugLayerData));

    updateLayerFunc = () => {
        updateFeatures(createDebugFeatures(debugLayerData));
    };

    return {
        layer: layer,
        searchItems: (_hitArea: Polygon, _options: SearchItemsOptions): LayerItemSearchResult => {
            return {};
        },
    };
}
