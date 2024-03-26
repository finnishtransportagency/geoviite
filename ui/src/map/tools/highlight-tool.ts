import OlMap from 'ol/Map';
import { Coordinate } from 'ol/coordinate';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { MapLayer } from 'map/layers/utils/layer-model';
import { debounce } from 'ts-debounce';
import { getDefaultHitArea, searchItemsFromLayers } from 'map/tools/tool-utils';
import { OnHighlightItemsOptions } from 'selection/selection-model';

let currentItemsCompare = '';

export const doHighlight = (
    coordinate: Coordinate,
    map: OlMap,
    layers: MapLayer[],
    onHighlightItems: (options: OnHighlightItemsOptions) => void,
) => {
    const hitArea = getDefaultHitArea(map, coordinate);
    const items = searchItemsFromLayers(hitArea, layers, { limit: 1 });
    const itemsCompare = JSON.stringify(items);
    if (currentItemsCompare != itemsCompare) {
        onHighlightItems(items);
        currentItemsCompare = itemsCompare;
    }
};

export const doForcedHighlight = (
    coordinate: Coordinate,
    map: OlMap,
    layers: MapLayer[],
    onHighlightItems: (options: OnHighlightItemsOptions) => void,
) => {
    const hitArea = getDefaultHitArea(map, coordinate);
    const items = searchItemsFromLayers(hitArea, layers, { limit: 1 });
    const itemsCompare = JSON.stringify(items);
    onHighlightItems(items);
    currentItemsCompare = itemsCompare;
};

export const highlightTool: MapTool = {
    activate: (map: OlMap, layers: MapLayer[], options: MapToolActivateOptions) => {
        const debouncedMoveHandlerHighlight = debounce(
            ({ coordinate }) => doHighlight(coordinate, map, layers, options.onHighlightItems),
            10,
            {
                maxWait: 25,
            },
        );
        const pointerMoveEvent = map.on('pointermove', debouncedMoveHandlerHighlight);

        // Return function to clean up this tool
        return () => {
            map.un('pointermove', pointerMoveEvent.listener);
        };
    },
};
