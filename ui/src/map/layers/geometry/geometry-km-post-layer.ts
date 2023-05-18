import OlPoint from 'ol/geom/Point';
import { Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Selection } from 'selection/selection-model';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { getMatchingKmPosts } from 'map/layers/utils/layer-utils';
import { GeometryPlanId } from 'geometry/geometry-model';
import { PublishType } from 'common/common-model';
import { getPlanLinkStatus } from 'linking/linking-api';
import { createKmPostFeature, getKmPostStepByResolution } from '../km-post/km-post-layer-utils';

let newestGeometryKmPostLayerId = 0;

export function createGeometryKmPostLayer(
    resolution: number,
    existingOlLayer: VectorLayer<VectorSource<OlPoint | Polygon>> | undefined,
    selection: Selection,
    publishType: PublishType,
): MapLayer {
    const layerId = ++newestGeometryKmPostLayerId;
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource, style: null });

    const step = getKmPostStepByResolution(resolution);

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

        Promise.all(planStatusPromises)
            .then((statusResults) => {
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

                    return createKmPostFeature(
                        kmPosts,
                        isSelected,
                        'geometryKmPost',
                        resolution,
                        statusResult.plan.planId,
                        (kmPost) =>
                            (kmPost.sourceId && kmPostLinkedStatus?.get(kmPost.sourceId)) || false,
                    );
                });

                if (layerId === newestGeometryKmPostLayerId) {
                    vectorSource.clear();
                    vectorSource.addFeatures(features);
                }
            })
            .catch(vectorSource.clear);
    } else {
        vectorSource.clear();
    }

    return {
        name: 'geometry-km-post-layer',
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
}
