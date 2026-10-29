import { Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapLayerName, MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { LayoutKmPost, LayoutKmPostId } from 'track-layout/track-layout-model';
import { getKmPosts, getKmPostsByTile } from 'track-layout/layout-km-post-api';
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
        Promise.all([
            step === 0
                ? Promise.resolve([])
                : Promise.all(
                      mapTiles.map(({ area }) =>
                          getKmPostsByTile(layoutContext, changeTimes.layoutKmPost, area, step),
                      ),
                  ).then((kmPostGroups) =>
                      kmPostGroups.flat().filter(filterUniqueById((kmPost) => kmPost.id)),
                  ),
            selection.selectedItems.kmPosts.length > 0
                ? getKmPosts(
                      selection.selectedItems.kmPosts,
                      layoutContext,
                      changeTimes.layoutKmPost,
                  )
                : Promise.resolve([]),
        ]).then(([shownKmPosts, selectedKmPosts]) => ({ shownKmPosts, selectedKmPosts }));

    function updateShownKmPosts(kmPostIds: LayoutKmPostId[]) {
        const compare = kmPostIds.sort().join();

        if (compare !== shownKmPostsCompare) {
            shownKmPostsCompare = compare;
            onViewContentChanged({ kmPosts: kmPostIds });
        }
    }

    const step = getKmPostStepByResolution(resolution);
    const dataPromise = getKmPostsFromApi(step);

    const createFeatures = ({
        shownKmPosts,
        selectedKmPosts,
    }: {
        shownKmPosts: LayoutKmPost[];
        selectedKmPosts: LayoutKmPost[];
    }) => {
        const isSelected = (kmPost: LayoutKmPost) => {
            return selection.selectedItems.kmPosts.some((k) => k === kmPost.id);
        };
        const isHighlighted = (kmPost: LayoutKmPost) => {
            return selection.highlightedItems.kmPosts.some((k) => k === kmPost.id);
        };
        return createKmPostFeatures(
            [...shownKmPosts, ...selectedKmPosts].filter(filterUniqueById((kmPost) => kmPost.id)),
            isSelected,
            isHighlighted,
            'layoutKmPost',
            resolution,
        );
    };

    const onLoadingChange = (
        loading: boolean,
        kmPosts: { shownKmPosts: LayoutKmPost[]; selectedKmPosts: LayoutKmPost[] } | undefined,
    ) => {
        if (!loading) {
            updateShownKmPosts(kmPosts?.shownKmPosts.map((kmp) => kmp.id) ?? []);
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
