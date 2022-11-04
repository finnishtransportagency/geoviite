import { Polygon } from 'ol/geom';
import OlMap from 'ol/Map';
import { LayerItemSearchResult, OlLayerAdapter, SearchItemsOptions } from 'map/layers/layer-model';
import {
    mergePartialItemSearchResults,
    mergePartialShownItemSearchResults,
} from 'map/layers/layer-utils';
import { OptionalShownItems } from 'map/map-model';

/**
 * Returns a simple shape that has consistent size in pixels and can be used to search items from layers.
 *
 */
export function getDefaultHitArea(map: OlMap, coordinate: number[], tolerance = 10): Polygon {
    const pixelLocation = map.getPixelFromCoordinate(coordinate);
    return new Polygon([
        [
            map.getCoordinateFromPixel([
                pixelLocation[0] - tolerance,
                pixelLocation[1] - tolerance,
            ]),
            map.getCoordinateFromPixel([
                pixelLocation[0] - tolerance,
                pixelLocation[1] + tolerance,
            ]),
            map.getCoordinateFromPixel([
                pixelLocation[0] + tolerance,
                pixelLocation[1] + tolerance,
            ]),
            map.getCoordinateFromPixel([
                pixelLocation[0] + tolerance,
                pixelLocation[1] - tolerance,
            ]),
            map.getCoordinateFromPixel([
                pixelLocation[0] - tolerance,
                pixelLocation[1] - tolerance,
            ]),
        ],
    ]);
}

export function searchShownItemsFromLayers(
    hitArea: Polygon,
    layerAdapters: OlLayerAdapter[],
    searchItemsOptions: SearchItemsOptions,
): OptionalShownItems {
    const searchResults = layerAdapters
        .filter((layerAdapter) => layerAdapter.layer.getVisible() && layerAdapter.searchItems)
        .map((layerAdapter) => {
            return layerAdapter.searchShownItems
                ? layerAdapter.searchShownItems(hitArea, searchItemsOptions)
                : {};
        });
    return mergePartialShownItemSearchResults(...searchResults);
}

export function searchItemsFromLayers(
    hitArea: Polygon,
    layerAdapters: OlLayerAdapter[],
    searchItemsOptions: SearchItemsOptions,
): LayerItemSearchResult {
    const searchResults = layerAdapters
        .filter((layerAdapter) => layerAdapter.layer.getVisible() && layerAdapter.searchItems)
        .map((layerAdapter) => {
            return layerAdapter.searchItems
                ? layerAdapter.searchItems(hitArea, searchItemsOptions)
                : {};
        });
    return mergePartialItemSearchResults(...searchResults);
}
