import Feature from 'ol/Feature';
import { Polygon } from 'ol/geom';
import OlPoint from 'ol/geom/Point';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Selection } from 'selection/selection-model';
import { Debug1mPointsLayer } from 'map/map-model';
import { LayerItemSearchResult, OlLayerAdapter, SearchItemsOptions } from 'map/layers/layer-model';
import { filterNotEmpty } from 'utils/array-utils';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';
import { AddressPoint, PublishType } from 'common/common-model';
import { AlignmentAddresses, getAddressPoints } from 'common/geocoding-api';
import { roundToPrecision } from 'utils/rounding';
import { DEBUG_1M_POINTS } from './layer-visibility-limits';

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
    const distance = roundToPrecision(point.distance, 1);
    return {
        x: point.point.x,
        y: point.point.y,
        color: color,
        text: `${name}=${address} (${distance})`,
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

function createDebugFeature(
    item: DebugLayerPoint,
): Feature<Debug1mPointsLayerFeatureType> | undefined {
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
    return data.flatMap((item: DebugLayerPoint) => createDebugFeature(item)).filter(filterNotEmpty);
}

export function createDebug1mPointsLayerAdapter(
    mapLayer: Debug1mPointsLayer,
    existingOlLayer: VectorLayer<VectorSource<Debug1mPointsLayerFeatureType>> | undefined,
    selection: Selection,
    publishType: PublishType,
    resolution: number | undefined,
): OlLayerAdapter {
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
    const layer: VectorLayer<VectorSource<Debug1mPointsLayerFeatureType>> =
        existingOlLayer ||
        new VectorLayer({
            source: vectorSource,
        });

    function clearFeatures() {
        vectorSource.clear();
    }

    function updateFeatures(features: Feature<Debug1mPointsLayerFeatureType>[]) {
        clearFeatures();
        vectorSource.addFeatures(features);
    }

    layer.setVisible(mapLayer.visible);

    const selected =
        selection.selectedItems.locationTracks.length > 0
            ? selection.selectedItems.locationTracks[0]
            : undefined;
    if (selected && resolution && resolution < DEBUG_1M_POINTS) {
        getAddressPoints(selected, publishType).then((data) =>
            data ? updateFeatures(createAddressPointFeatures(data)) : updateFeatures([]),
        );
    } else {
        updateFeatures([]);
    }

    return {
        layer: layer,
        searchItems: (_hitArea: Polygon, _options: SearchItemsOptions): LayerItemSearchResult => {
            return {};
        },
    };
}
