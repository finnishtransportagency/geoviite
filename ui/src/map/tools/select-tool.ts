import OlMap from 'ol/Map';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { getDefaultHitArea, searchItemsFromLayers } from 'map/tools/tool-utils';
import { MapLayer } from 'map/layers/utils/layer-model';

export const selectTool: MapTool = {
    activate: (map: OlMap, layers: MapLayer[], options: MapToolActivateOptions) => {
        const clickEvent = map.on('click', ({ coordinate }) => {
            const hitArea = getDefaultHitArea(map, coordinate);
            const items = searchItemsFromLayers(hitArea, layers, { limit: 1 });

            options.onSelect({
                ...items,
                geometryKmPosts: items.geometryKmPosts ?? [],
                kmPosts: items.kmPosts ?? [],
                geometrySwitches: items.geometrySwitches ?? [],
                switches: items.switches ?? [],
                geometryAlignments: items.geometryAlignments ?? [],
                locationTracks: items.locationTracks ?? [],
                trackNumbers: items.trackNumbers ?? [],
                geometryPlans: items.geometryPlans ?? [],
            });
        });

        // Return function to clean up this tool
        return () => {
            map.un('click', clickEvent.listener);
        };
    },
};
