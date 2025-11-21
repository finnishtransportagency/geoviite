import OlMap from 'ol/Map';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { MapLayer } from 'map/layers/utils/layer-model';
import { debounce } from 'ts-debounce';
import { getDefaultHitArea, searchItemsFromLayers, searchItemsFromMap } from 'map/tools/tool-utils';
import { mergePartialItemSearchResults } from 'map/layers/utils/layer-utils';
import { createEmptyItemCollections } from 'selection/selection-store';

let currentItemsCompare = '';
export const highlightTool: MapTool = {
    id: 'highlight',
    activate: (map: OlMap, layers: MapLayer[], options: MapToolActivateOptions) => {
        const debouncedMoveHandlerHighlight = debounce(
            ({ coordinate }) => {
                const hitArea = getDefaultHitArea(map, coordinate);
                const itemsFromLayers = searchItemsFromLayers(hitArea, layers, { limit: 1 });
                const itemsFromMap = createEmptyItemCollections(); //searchItemsFromMap(coordinate, map, { limit: 1 });
                const items = mergePartialItemSearchResults(itemsFromLayers, itemsFromMap);

                const itemsCompare = JSON.stringify(items);
                if (currentItemsCompare !== itemsCompare) {
                    options.onHighlightItems(items);
                    currentItemsCompare = itemsCompare;
                }
            },
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
