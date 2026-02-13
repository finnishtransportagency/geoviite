import OlMap from 'ol/Map';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { getDefaultHitArea, searchItemsFromLayers } from 'map/tools/tool-utils';
import { MapLayer } from 'map/layers/utils/layer-model';

export const selectTool: MapTool = {
    id: 'select',
    activate: (map: OlMap, layers: MapLayer[], options: MapToolActivateOptions) => {
        const clickEvent = map.on('click', ({ coordinate }) => {
            const hitArea = getDefaultHitArea(map, coordinate);
            const items = searchItemsFromLayers(hitArea, layers, { limit: 1 });

            options.onSelect({
                ...items,
                geometryKmPostIds: items.geometryKmPostIds ?? [],
                kmPosts: items.kmPosts ?? [],
                geometrySwitchIds: items.geometrySwitchIds ?? [],
                switches: items.switches ?? [],
                geometryAlignmentIds: items.geometryAlignmentIds ?? [],
                locationTracks: items.locationTracks ?? [],
                trackNumbers: items.trackNumbers ?? [],
                geometryPlans: items.geometryPlans ?? [],
                operationalPoints: items.operationalPoints ?? [],
                operationalPointClusters: items.operationalPointClusters ?? [],
            });
        });

        // Return function to clean up this tool
        return () => {
            map.un('click', clickEvent.listener);
        };
    },
};
