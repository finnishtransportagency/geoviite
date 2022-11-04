import OlMap from 'ol/Map';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { OlLayerAdapter } from 'map/layers/layer-model';
import { getDefaultHitArea, searchItemsFromLayers } from 'map/tools/tool-utils';

export const selectToolBasic: MapTool = {
    icon: 'fas fa-mouse-pointer',
    activate: (map: OlMap, layerAdapters: OlLayerAdapter[], options: MapToolActivateOptions) => {
        const clickEvent = map.on('click', (e) => {
            const hitArea = getDefaultHitArea(map, e.coordinate);
            const items = searchItemsFromLayers(hitArea, layerAdapters, { limit: 1 });
            options.onSelect(items);
        });

        // Return function to clean up this tool
        return () => {
            map.un('click', clickEvent.listener);
        };
    },
};
