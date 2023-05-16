import OlMap from 'ol/Map';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { getDefaultHitArea, searchItemsFromLayers } from 'map/tools/tool-utils';
import { MapLayer } from 'map/layers/layer-model';

export const selectTool: MapTool = {
    activate: (map: OlMap, layers: MapLayer[], options: MapToolActivateOptions) => {
        const clickEvent = map.on('click', (e) => {
            const hitArea = getDefaultHitArea(map, e.coordinate);
            const items = searchItemsFromLayers(hitArea, layers, { limit: 1 });
            options.onSelect(items);
        });

        // Return function to clean up this tool
        return () => {
            map.un('click', clickEvent.listener);
        };
    },
};
