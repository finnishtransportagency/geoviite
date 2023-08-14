import { Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { LayoutKmPost, LayoutKmPostId } from 'track-layout/track-layout-model';
import { getKmPostsByTile } from 'track-layout/layout-km-post-api';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import {
    createKmPostFeatures,
    findMatchingKmPosts,
    getKmPostStepByResolution,
} from 'map/layers/utils/km-post-layer-utils';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { filterUniqueById } from 'utils/array-utils';

let shownKmPostsCompare: string;
let newestLayerId = 0;

export function createKmPostLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint | Rectangle>> | undefined,
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
        ).then((kmPostGroups) =>
            kmPostGroups.flat().filter(filterUniqueById((kmPost) => kmPost.id)),
        );

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource, style: null });

    function updateShownKmPosts(kmPostIds: LayoutKmPostId[]) {
        const compare = kmPostIds.sort().join();

        if (compare !== shownKmPostsCompare) {
            shownKmPostsCompare = compare;
            onViewContentChanged({ kmPosts: kmPostIds });
        }
    }

    let inFlight = false;
    const step = getKmPostStepByResolution(resolution);
    if (step == 0) {
        clearFeatures(vectorSource);
        updateShownKmPosts([]);
    } else {
        inFlight = true;
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
            })
            .finally(() => {
                inFlight = false;
            });
    }

    return {
        name: 'km-post-layer',
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => {
            return {
                kmPosts: findMatchingKmPosts(hitArea, vectorSource, options).map(
                    ({ kmPost }) => kmPost.id,
                ),
            };
        },
        onRemove: () => updateShownKmPosts([]),
        requestInFlight: () => inFlight,
    };
}
