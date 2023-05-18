import OlPoint from 'ol/geom/Point';
import { Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Selection } from 'selection/selection-model';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { clearFeatures, getMatchingKmPosts } from 'map/layers/utils/layer-utils';
import { GeometryPlanId } from 'geometry/geometry-model';
import { PublishType } from 'common/common-model';
import { getPlanLinkStatus } from 'linking/linking-api';
import { createKmPostFeatures, getKmPostStepByResolution } from '../km-post/km-post-layer-utils';

let newestLayerId = 0;

export function createGeometryKmPostLayer(
    resolution: number,
    existingOlLayer: VectorLayer<VectorSource<OlPoint | Polygon>> | undefined,
    selection: Selection,
    publishType: PublishType,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    const step = getKmPostStepByResolution(resolution);

    if (step) {
        const isSelected = (kmPost: LayoutKmPost) => {
            return selection.selectedItems.geometryKmPosts.some(
                ({ geometryItem }) => geometryItem.id === kmPost.id,
            );
        };

        const planStatusPromises = selection.planLayouts.map((plan) =>
            plan.planDataType == 'STORED'
                ? getPlanLinkStatus(plan.planId, publishType).then((status) => ({ plan, status }))
                : { plan, status: undefined },
        );

        Promise.all(planStatusPromises)
            .then((planStatuses) => {
                if (layerId !== newestLayerId) return;

                const features = planStatuses.flatMap(({ plan, status }) => {
                    const kmPosts = plan.kmPosts.filter(
                        ({ kmNumber }) => Number.parseInt(kmNumber) % step === 0,
                    );

                    const kmPostLinkedStatus = status
                        ? new Map(
                              status.kmPosts.map((kmPosts) => [
                                  kmPosts.id,
                                  kmPosts.linkedKmPosts?.length > 0,
                              ]),
                          )
                        : undefined;

                    return createKmPostFeatures(
                        kmPosts,
                        isSelected,
                        'geometryKmPost',
                        resolution,
                        plan.planId,
                        (kmPost) =>
                            (kmPost.sourceId && kmPostLinkedStatus?.get(kmPost.sourceId)) || false,
                    );
                });

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'geometry-km-post-layer',
        layer: layer,
        searchItems: (hitArea: Polygon, options: SearchItemsOptions): LayerItemSearchResult => {
            const geometryKmPosts = getMatchingKmPosts(
                hitArea,
                vectorSource.getFeaturesInExtent(hitArea.getExtent()),
                {
                    strategy: 'nearest',
                    limit: options.limit,
                },
            ).map((d) => ({
                geometryItem: d.kmPost,
                planId: d.planId as GeometryPlanId,
            }));

            return { geometryKmPosts };
        },
    };
}
