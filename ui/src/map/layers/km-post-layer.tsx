import Feature from 'ol/Feature';
import OlPoint from 'ol/geom/Point';
import OlView from 'ol/View';
import { Polygon } from 'ol/geom';
import { Layer as OlLayer, Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Style } from 'ol/style';
import { KmPostLayer, MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { adapterInfoRegister } from './register';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { getKmPostsByTile } from 'track-layout/layout-km-post-api';
import { LayerItemSearchResult, OlLayerAdapter, SearchItemsOptions } from 'map/layers/layer-model';
import { getMatchingKmPosts } from 'map/layers/layer-utils';
import { getKmPostRenderer, getSelectedKmPostRenderer } from 'map/layers/km-post-renderers';
import { calculateTileSize } from 'map/map-utils';
import { ChangeTimes } from 'track-layout/track-layout-store';
import { fromExtent } from 'ol/geom/Polygon';
import { LinkingState } from 'linking/linking-model';
import { GeometryPlanId } from 'geometry/geometry-model';
import { PublishType, TimeStamp } from 'common/common-model';
import { getPlanLinkStatus } from 'linking/linking-api';
import { Point } from 'model/geometry';

export type KmPostType = 'layoutKmPost' | 'geometryKmPost';

/**
 * Creates OL feature objects by location track.
 */
function createFeatures(
    kmPosts: LayoutKmPost[],
    isSelected: (kmPost: LayoutKmPost) => boolean,
    kmPostType: KmPostType,
    resolution: number,
    planId?: GeometryPlanId,
    isLinked: ((kmPost: LayoutKmPost) => boolean) | undefined = undefined,
): Feature<OlPoint | Polygon>[] {
    return kmPosts
        .filter((kmPost) => kmPost.location != null)
        .flatMap((kmPost) => {
            const location = kmPost.location as Point;
            const point = new OlPoint([location.x, location.y]);
            const feature = new Feature<OlPoint>({
                geometry: point,
            });

            const selected = isSelected(kmPost);

            feature.setStyle(() => {
                return new Style({
                    zIndex: selected ? 1 : 0,
                    renderer: selected
                        ? getSelectedKmPostRenderer(
                              kmPost,
                              kmPostType,
                              isLinked && isLinked(kmPost),
                          )
                        : getKmPostRenderer(kmPost, kmPostType, isLinked && isLinked(kmPost)),
                });
            });
            feature.set('kmPost-data', {
                kmPost: kmPost,
                planId: planId,
            });

            // Create a feature to act as a clickable area
            const width = 35 * resolution;
            const height = 15 * resolution;
            const clickableX = location.x - 5 * resolution; // offset x a bit
            const polygon = new Polygon([
                [
                    [clickableX, location.y - height / 2],
                    [clickableX + width, location.y - height / 2],
                    [clickableX + width, location.y + height / 2],
                    [clickableX, location.y + height / 2],
                    [clickableX, location.y - height / 2],
                ],
            ]);
            const hitAreaFeature = new Feature<Polygon>({
                geometry: polygon,
            });
            hitAreaFeature.setStyle(undefined);
            hitAreaFeature.set('kmPost-data', {
                kmPost: kmPost,
                planId: planId,
            });

            return [feature, hitAreaFeature];
        });
}

/**
 * Steps of km post skip step
 */
const stepSteps = [1, 2, 5, 10, 20, 50, 100];

function getStepByResolution(resolution: number): number {
    const step = Math.ceil(resolution / 10);
    return stepSteps.find((stepStep) => step <= stepStep) || 0;
}

let kmPostIdCompare = '';
let kmPostChangeTimeCompare: TimeStamp | undefined = undefined;

adapterInfoRegister.add('kmPosts', {
    createAdapter: function (
        mapTiles: MapTile[],
        existingOlLayer: OlLayer<VectorSource<OlPoint | Polygon>> | undefined,
        mapLayer: KmPostLayer,
        selection: Selection,
        publishType: PublishType,
        _linkingState: LinkingState | undefined,
        changeTimes: ChangeTimes,
        olView: OlView,
        onViewContentChanged?: (items: OptionalShownItems) => void,
    ): OlLayerAdapter {
        const resolution = olView.getResolution() || 0;
        const getKmPostFromApi = (step: number) =>
            Promise.all(
                mapTiles.map((tile) =>
                    getKmPostsByTile(publishType, changeTimes.layoutKmPost, tile.area, step),
                ),
            ).then((kmPostGroups) => [...new Set(kmPostGroups.flat())]);
        const vectorSource = existingOlLayer?.getSource() || new VectorSource();

        // Use an existing layer or create a new one. Old layer is "recycled" to
        // prevent features to disappear while moving the map.
        const layer: VectorLayer<VectorSource<OlPoint | Polygon>> =
            (existingOlLayer as VectorLayer<VectorSource<OlPoint | Polygon>>) ||
            new VectorLayer({
                source: vectorSource,
            });
        layer.setStyle(null); // No default styling for features

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

        layer.setVisible(mapLayer.visible);

        const step = getStepByResolution(mapTiles[0].resolution);
        if (step == 0) {
            // Do not fetch
            vectorSource.clear();
        } else {
            // Fetch every nth
            const isSelected = (kmPost: LayoutKmPost) => {
                return selection.selectedItems.kmPosts.some((k) => k === kmPost.id);
            };
            const updateFeaturesPromise = getKmPostFromApi(step).then((kmPosts) => {
                // Handle latest fetch only
                if (layer.get('updateFeaturesPromise') === updateFeaturesPromise) {
                    const features = createFeatures(
                        kmPosts,
                        isSelected,
                        'layoutKmPost',
                        resolution,
                    );
                    return updateFeatures(kmPosts, features);
                }
            });
            layer.set('updateFeaturesPromise', updateFeaturesPromise);
        }

        return {
            layer: layer,
            searchItems: searchFunction,
            searchShownItems: searchFunction,
        };
    },

    // Use larger map tile size for km posts as each tile contains quite a small amount of data
    mapTileSizePx: calculateTileSize(2),
});

adapterInfoRegister.add('geometryKmPosts', {
    createAdapter: function (
        mapTiles: MapTile[],
        existingOlLayer: VectorLayer<VectorSource<OlPoint | Polygon>> | undefined,
        mapLayer: KmPostLayer,
        selection: Selection,
        publishType: PublishType,
        _linkingState: LinkingState | undefined,
        _changeTimes: ChangeTimes,
        olView: OlView,
    ): OlLayerAdapter {
        const vectorSource = existingOlLayer?.getSource() || new VectorSource();

        // Use an existing layer or create a new one. Old layer is "recycled" to
        // prevent features to disappear while moving the map.
        const layer: VectorLayer<VectorSource<OlPoint | Polygon>> =
            existingOlLayer ||
            new VectorLayer({
                source: vectorSource,
            });
        layer.setStyle(null); // No default styling for features
        layer.setVisible(mapLayer.visible);

        const step = getStepByResolution(mapTiles[0].resolution);

        vectorSource.clear();
        if (step) {
            const isSelected = (kmPost: LayoutKmPost) => {
                return selection.selectedItems.geometryKmPosts.some(
                    (k) => k.geometryItem.id === kmPost.id,
                );
            };

            const planStatusPromises = selection.planLayouts.map((plan) =>
                plan.planDataType == 'STORED'
                    ? getPlanLinkStatus(plan.planId, publishType).then((status) => ({
                          plan: plan,
                          status: status,
                      }))
                    : {
                          plan: plan,
                          status: undefined,
                      },
            );

            Promise.all(planStatusPromises).then((statusResults) => {
                const features = statusResults.flatMap((statusResult) => {
                    const kmPosts = statusResult.plan.kmPosts.filter(
                        (kmPost) => Number.parseInt(kmPost.kmNumber) % step === 0,
                    );
                    const kmPostLinkedStatus = statusResult.status
                        ? new Map(
                              statusResult.status.kmPosts.map((kmPosts) => [
                                  kmPosts.id,
                                  kmPosts.linkedKmPosts?.length > 0,
                              ]),
                          )
                        : undefined;

                    return createFeatures(
                        kmPosts,
                        isSelected,
                        'geometryKmPost',
                        olView.getResolution() || 0,
                        statusResult.plan.planId,
                        (kmPost) =>
                            (kmPost.sourceId && kmPostLinkedStatus?.get(kmPost.sourceId)) || false,
                    );
                });

                vectorSource.clear();
                vectorSource.addFeatures(features);
            });
        }

        return {
            layer: layer,
            searchItems: (hitArea: Polygon, options: SearchItemsOptions): LayerItemSearchResult => {
                const kmPosts = getMatchingKmPosts(
                    hitArea,
                    vectorSource.getFeaturesInExtent(hitArea.getExtent()),
                    {
                        strategy: 'nearest',
                        limit: options.limit,
                    },
                ).map((d) => {
                    return {
                        geometryItem: d.kmPost,
                        planId: d.planId as GeometryPlanId,
                    };
                });

                return {
                    geometryKmPosts: kmPosts,
                };
            },
        };
    },

    // Use a large map tile size as all km posts of selected plans are always loaded
    mapTileSizePx: calculateTileSize(0),
});
