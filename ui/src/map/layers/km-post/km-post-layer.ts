import { Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapLayerName, MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { LayoutKmPost, LayoutKmPostId } from 'track-layout/track-layout-model';
import { getKmPostsByTile } from 'track-layout/layout-km-post-api';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { ChangeTimes } from 'common/common-slice';
import {
    createKmPostFeatures,
    findMatchingKmPosts,
    getKmPostStepByResolution,
} from 'map/layers/utils/km-post-layer-utils';
import { Rectangle } from 'model/geometry';
import { filterUniqueById } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';

let shownKmPostsCompare: string;

const layerName: MapLayerName = 'km-post-layer';

export function createKmPostLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<OlPoint | Rectangle> | undefined,
    selection: Selection,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged: (items: OptionalShownItems) => void,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer, false);

    const resolution = olView.getResolution() || 0;
    const getKmPostsFromApi = (step: number) =>
        // Fetch every nth
        step === 0
            ? Promise.resolve([])
            : Promise.all(
                  mapTiles.map(({ area }) =>
                      getKmPostsByTile(layoutContext, changeTimes.layoutKmPost, area, step),
                  ),
              ).then((kmPostGroups) =>
                  kmPostGroups.flat().filter(filterUniqueById((kmPost) => kmPost.id)),
              );

    function updateShownKmPosts(kmPostIds: LayoutKmPostId[]) {
        const compare = kmPostIds.sort().join();

        if (compare !== shownKmPostsCompare) {
            shownKmPostsCompare = compare;
            onViewContentChanged({ kmPosts: kmPostIds });
        }
    }

    const step = getKmPostStepByResolution(resolution);
    const dataPromise: Promise<LayoutKmPost[]> = getKmPostsFromApi(step);

    const createFeatures = (kmPosts: LayoutKmPost[]) => {
        const isSelected = (kmPost: LayoutKmPost) => {
            return selection.selectedItems.kmPosts.some((k) => k === kmPost.id);
        };
        return createKmPostFeatures(kmPosts, isSelected, 'layoutKmPost', resolution);
    };

    const onLoadingChange = (loading: boolean, kmPosts: LayoutKmPost[] | undefined) => {
        if (!loading) {
            updateShownKmPosts(kmPosts?.map((kmp) => kmp.id) ?? []);
        }
        onLoadingData(loading);
    };

    loadLayerData(source, isLatest, onLoadingChange, dataPromise, createFeatures);

    return {
        name: layerName,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => ({
            kmPosts: findMatchingKmPosts(hitArea, source, options).map(({ kmPost }) => kmPost.id),
        }),
        onRemove: () => updateShownKmPosts([]),
    };
}
