import { Polygon as OlPolygon } from 'ol/geom';
import OlMap from 'ol/Map';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { mergePartialItemSearchResults } from 'map/layers/utils/layer-utils';
import { Rectangle } from 'model/geometry';
import { expectCoordinate } from 'utils/type-utils';

/**
 * Returns a simple shape that has consistent size in pixels and can be used to search items from layers.
 *
 */
export function getDefaultHitArea(map: OlMap, coordinate: number[], tolerance = 10): Rectangle {
    const pixel = map.getPixelFromCoordinate(coordinate);
    const [x, y] = expectCoordinate(pixel);
    return new OlPolygon([
        [
            map.getCoordinateFromPixel([x - tolerance, y - tolerance]),
            map.getCoordinateFromPixel([x - tolerance, y + tolerance]),
            map.getCoordinateFromPixel([x + tolerance, y + tolerance]),
            map.getCoordinateFromPixel([x + tolerance, y - tolerance]),
            map.getCoordinateFromPixel([x - tolerance, y - tolerance]),
        ],
    ]);
}

export function searchItemsFromLayers(
    hitArea: Rectangle,
    layers: MapLayer[],
    searchItemsOptions: SearchItemsOptions,
): LayerItemSearchResult {
    const searchResults = layers.map((layer) => {
        return layer.searchItems ? layer.searchItems(hitArea, searchItemsOptions) : {};
    });
    return mergePartialItemSearchResults(...searchResults);
}
