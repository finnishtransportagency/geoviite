import Feature from 'ol/Feature';
import OlPoint from 'ol/geom/Point';
import OlView from 'ol/View';
import { Polygon } from 'ol/geom';
import { Layer as OlLayer, Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { getKmPostsByTile } from 'track-layout/layout-km-post-api';
import { MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { getMatchingKmPosts } from 'map/layers/utils/layer-utils';
import { fromExtent } from 'ol/geom/Polygon';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import {
    createKmPostFeature,
    getKmPostStepByResolution,
} from 'map/layers/km-post/km-post-layer-utils';

let kmPostIdCompare: string;
let kmPostChangeTimeCompare: string;
let newestKmPostsLayerId = 0;

export function createKmPostLayer(
    mapTiles: MapTile[],
    existingOlLayer: OlLayer<VectorSource<OlPoint | Polygon>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged?: (items: OptionalShownItems) => void,
): MapLayer {
    const layerId = ++newestKmPostsLayerId;
    const resolution = olView.getResolution() || 0;
    const getKmPostsFromApi = (step: number) =>
        Promise.all(
            mapTiles.map((tile) =>
                getKmPostsByTile(publishType, changeTimes.layoutKmPost, tile.area, step),
            ),
        ).then((kmPostGroups) => [...new Set(kmPostGroups.flat())]);
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource, style: null });

    const searchFunction = (hitArea: Polygon, options: SearchItemsOptions) => {
        const kmPosts = getMatchingKmPosts(
            hitArea,
            vectorSource.getFeaturesInExtent(hitArea.getExtent()),
            {
                strategy: 'nearest',
                limit: options.limit,
            },
        ).map((d) => d.kmPost.id);

        return {
            kmPosts: kmPosts,
        };
    };

    function updateFeatures(kmPosts: LayoutKmPost[], features: Feature<OlPoint | Polygon>[]) {
        vectorSource.clear();
        vectorSource.addFeatures(features);

        if (onViewContentChanged) {
            const newIds = JSON.stringify(kmPosts.map((p) => p.id).sort());

            const changeTimeCompare = changeTimes.layoutKmPost;
            if (newIds !== kmPostIdCompare || changeTimeCompare !== kmPostChangeTimeCompare) {
                kmPostIdCompare = newIds;
                kmPostChangeTimeCompare = changeTimeCompare;
                const area = fromExtent(olView.calculateExtent());
                const result = searchFunction(area, {});
                onViewContentChanged(result);
            }
        }
    }

    const step = getKmPostStepByResolution(resolution);
    if (step == 0) {
        // Do not fetch
        vectorSource.clear();
    } else {
        // Fetch every nth
        getKmPostsFromApi(step).then((kmPosts) => {
            // Handle latest fetch only
            if (layerId == newestKmPostsLayerId) {
                const isSelected = (kmPost: LayoutKmPost) => {
                    return selection.selectedItems.kmPosts.some((k) => k === kmPost.id);
                };

                const features = createKmPostFeature(
                    kmPosts,
                    isSelected,
                    'layoutKmPost',
                    resolution,
                );
                updateFeatures(kmPosts, features);
            }
        });
    }

    return {
        name: 'km-post-layer',
        layer: layer,
        searchItems: searchFunction,
        searchShownItems: searchFunction,
    };
}
