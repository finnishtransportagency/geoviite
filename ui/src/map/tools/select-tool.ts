import OlMap from 'ol/Map';
import { MapTool, MapToolActivateOptions, MapToolHandle } from './tool-model';
import { getDefaultHitArea, searchItemsFromLayers } from 'map/tools/tool-utils';
import { MapLayer } from 'map/layers/utils/layer-model';

export const selectTool: MapTool = {
    id: 'select',
    activate: (
        map: OlMap,
        initialLayers: MapLayer[],
        options: MapToolActivateOptions,
    ): MapToolHandle => {
        let layers = initialLayers;

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

        return {
            deactivate: () => {
                map.un('click', clickEvent.listener);
            },
            onLayersChanged: (newLayers) => {
                layers = newLayers;
            },
        };
    },
};
