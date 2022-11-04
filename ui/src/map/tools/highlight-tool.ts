import OlMap from 'ol/Map';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { OlLayerAdapter } from 'map/layers/layer-model';
import { debounce } from 'ts-debounce';
import { getDefaultHitArea, searchItemsFromLayers } from 'map/tools/tool-utils';

let currentItemsCompare = '';
export const highlightTool: MapTool = {
    icon: '',
    activate: (map: OlMap, layerAdapters: OlLayerAdapter[], options: MapToolActivateOptions) => {
        const debouncedMoveHandlerHighlight = debounce(
            (e) => {
                const hitArea = getDefaultHitArea(map, e.coordinate);
                const items = searchItemsFromLayers(hitArea, layerAdapters, { limit: 1 });
                const itemsCompare = JSON.stringify(items);
                if (currentItemsCompare != itemsCompare) {
                    options.onHighlightItems(items);
                    currentItemsCompare = itemsCompare;
                }
            },
            50,
            {
                maxWait: 100,
            },
        );
        const pointerMoveEvent = map.on('pointermove', debouncedMoveHandlerHighlight);

        // Return function to clean up this tool
        return () => {
            map.un('pointermove', pointerMoveEvent.listener);
        };
    },
};
