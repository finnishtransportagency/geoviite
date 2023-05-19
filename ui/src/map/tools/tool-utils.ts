import { Polygon } from 'ol/geom';
import OlMap from 'ol/Map';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import {
    mergePartialItemSearchResults,
    mergePartialShownItemSearchResults,
} from 'map/layers/utils/layer-utils';
import { OptionalShownItems } from 'map/map-model';

/**
 * Returns a simple shape that has consistent size in pixels and can be used to search items from layers.
 *
 */
export function getDefaultHitArea(map: OlMap, coordinate: number[], tolerance = 10): Polygon {
    const [x, y] = map.getPixelFromCoordinate(coordinate);
    return new Polygon([
        [
            map.getCoordinateFromPixel([x - tolerance, y - tolerance]),
            map.getCoordinateFromPixel([x - tolerance, y + tolerance]),
            map.getCoordinateFromPixel([x + tolerance, y + tolerance]),
            map.getCoordinateFromPixel([x + tolerance, y - tolerance]),
            map.getCoordinateFromPixel([x - tolerance, y - tolerance]),
        ],
    ]);
}

export function searchShownItemsFromLayers(
    hitArea: Polygon,
    layers: MapLayer[],
    searchItemsOptions: SearchItemsOptions,
): OptionalShownItems {
    const searchResults = layers
        .filter((layer) => layer.searchShownItems)
        .map((layer) => {
            return layer.searchShownItems
                ? layer.searchShownItems(hitArea, searchItemsOptions)
                : {};
        });
    return mergePartialShownItemSearchResults(...searchResults);
}

export function searchItemsFromLayers(
    hitArea: Polygon,
    layers: MapLayer[],
    searchItemsOptions: SearchItemsOptions,
): LayerItemSearchResult {
    const searchResults = layers
        .filter((l) => l.searchItems)
        .map((layer) => {
            return layer.searchItems ? layer.searchItems(hitArea, searchItemsOptions) : {};
        });
    return mergePartialItemSearchResults(...searchResults);
}
