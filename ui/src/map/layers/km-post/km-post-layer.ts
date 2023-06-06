import OlPoint from 'ol/geom/Point';
import OlView from 'ol/View';
import { Polygon } from 'ol/geom';
import { Layer as OlLayer, Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { LayoutKmPost, LayoutKmPostId } from 'track-layout/track-layout-model';
import { getKmPostsByTile } from 'track-layout/layout-km-post-api';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { clearFeatures, getMatchingKmPosts } from 'map/layers/utils/layer-utils';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import {
    createKmPostFeatures,
    getKmPostStepByResolution,
} from 'map/layers/km-post/km-post-layer-utils';

let shownKmPostsCompare: string;
let newestLayerId = 0;

export function createKmPostLayer(
    mapTiles: MapTile[],
    existingOlLayer: OlLayer<VectorSource<OlPoint | Polygon>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged: (items: OptionalShownItems) => void,
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

    function updateShownKmPosts(kmPostIds: LayoutKmPostId[]) {
        const compare = kmPostIds.sort().join();

        if (compare !== shownKmPostsCompare) {
            shownKmPostsCompare = compare;
            onViewContentChanged({ kmPosts: kmPostIds });
        }
    }

    const step = getKmPostStepByResolution(resolution);
    if (step == 0) {
        clearFeatures(vectorSource);
        updateShownKmPosts([]);
    } else {
        // Fetch every nth
        getKmPostsFromApi(step)
            .then((kmPosts) => {
                // Handle latest fetch only
                if (layerId !== newestLayerId) return;

                const isSelected = (kmPost: LayoutKmPost) => {
                    return selection.selectedItems.kmPosts.some((k) => k === kmPost.id);
                };

                const features = createKmPostFeatures(
                    kmPosts,
                    isSelected,
                    'layoutKmPost',
                    resolution,
                );

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);

                updateShownKmPosts(kmPosts.map((k) => k.id));
            })
            .catch(() => {
                clearFeatures(vectorSource);
                updateShownKmPosts([]);
            });
    }

    return {
        name: 'km-post-layer',
        layer: layer,
        searchItems: (hitArea: Polygon, options: SearchItemsOptions): LayerItemSearchResult => {
            const kmPosts = getMatchingKmPosts(
                hitArea,
                vectorSource.getFeaturesInExtent(hitArea.getExtent()),
                {
                    strategy: 'nearest',
                    limit: options.limit,
                },
            ).map(({ kmPost }) => kmPost.id);

            return { kmPosts };
        },
        onRemove: () => updateShownKmPosts([]),
    };
}
