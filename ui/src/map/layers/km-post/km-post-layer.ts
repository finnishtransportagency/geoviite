import Feature from 'ol/Feature';
import OlPoint from 'ol/geom/Point';
import OlView from 'ol/View';
import { Polygon } from 'ol/geom';
import { Layer as OlLayer, Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { LayoutKmPost, LayoutKmPostId } from 'track-layout/track-layout-model';
import { getKmPostsByTile } from 'track-layout/layout-km-post-api';
import { MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { clearFeatures, getMatchingKmPosts } from 'map/layers/utils/layer-utils';
import { fromExtent } from 'ol/geom/Polygon';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import {
    createKmPostFeatures,
    getKmPostStepByResolution,
} from 'map/layers/km-post/km-post-layer-utils';

let kmPostIdCompare: string;
let kmPostChangeTimeCompare: string;
let newestLayerId = 0;

export function createKmPostLayer(
    mapTiles: MapTile[],
    existingOlLayer: OlLayer<VectorSource<OlPoint | Polygon>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged?: (items: OptionalShownItems) => void,
): MapLayer {
    const layerId = ++newestLayerId;
    const resolution = olView.getResolution() || 0;
    const getKmPostsFromApi = (step: number) =>
        Promise.all(
            mapTiles.map(({ area }) =>
                getKmPostsByTile(publishType, changeTimes.layoutKmPost, area, step),
            ),
        ).then((kmPostGroups) => [...new Set(kmPostGroups.flat())]);

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource, style: null });

    const searchFunction = (hitArea: Polygon, options: SearchItemsOptions) => {
        const kmPosts = getMatchingKmPosts(
            hitArea,
            vectorSource.getFeaturesInExtent(hitArea.getExtent()),
            {
                strategy: 'nearest',
                limit: options.limit,
            },
        ).map(({ kmPost }) => kmPost.id);

        return { kmPosts };
    };

    function switchesChanged(kmPostIds: LayoutKmPostId[]) {
        if (onViewContentChanged) {
            const newIds = kmPostIds.sort().join();

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

    function updateFeatures(kmPosts: LayoutKmPost[], features: Feature<OlPoint | Polygon>[]) {
        clearFeatures(vectorSource);
        vectorSource.addFeatures(features);

        switchesChanged(kmPosts.map((k) => k.id));
    }

    const step = getKmPostStepByResolution(resolution);
    if (step == 0) {
        clearFeatures(vectorSource);
        switchesChanged([]);
    } else {
        // Fetch every nth
        getKmPostsFromApi(step)
            .then((kmPosts) => {
                // Handle latest fetch only
                if (layerId == newestLayerId) {
                    const isSelected = (kmPost: LayoutKmPost) => {
                        return selection.selectedItems.kmPosts.some((k) => k === kmPost.id);
                    };

                    const features = createKmPostFeatures(
                        kmPosts,
                        isSelected,
                        'layoutKmPost',
                        resolution,
                    );

                    updateFeatures(kmPosts, features);
                }
            })
            .catch(() => clearFeatures(vectorSource));
    }

    return {
        name: 'km-post-layer',
        layer: layer,
        searchItems: searchFunction,
        searchShownItems: searchFunction,
    };
}
