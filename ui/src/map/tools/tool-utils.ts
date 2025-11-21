import { Polygon as OlPolygon } from 'ol/geom';
import OlMap from 'ol/Map';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { mergePartialItemSearchResults } from 'map/layers/utils/layer-utils';
import { Rectangle } from 'model/geometry';
import { expectCoordinate } from 'utils/type-utils';
import { Coordinate } from 'ol/coordinate';
import { OPERATIONAL_POINT_FEATURE_DATA_PROPERTY } from 'map/layers/operational-point/operational-points-layer-utils';
import { OperationalPoint } from 'track-layout/track-layout-model';
import { createEmptyItemCollections } from 'selection/selection-store';

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

export function searchItemsFromMap(
    pixel: Coordinate,
    map: OlMap,
    _searchItemsOptions: SearchItemsOptions,
): LayerItemSearchResult {
    let searchResults: LayerItemSearchResult = createEmptyItemCollections();
    //const pixel = map.getPixelFromCoordinate(coord);
    console.log(map.getFeaturesAtPixel(pixel));
    map.forEachFeatureAtPixel(
        pixel,
        (feat) => {
            const point = feat.get(OPERATIONAL_POINT_FEATURE_DATA_PROPERTY) as
                | OperationalPoint
                | undefined;
            if (point) {
                //console.log(pixel, feat, point);
                searchResults = mergePartialItemSearchResults(searchResults, {
                    operationalPoints: [point.id],
                });
            }
        },
        {
            hitTolerance: 1,
        },
    );
    return mergePartialItemSearchResults(searchResults);
}
