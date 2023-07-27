import { Point as OlPoint } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import { PublishType } from 'common/common-model';
import { getPlanLinkStatus } from 'linking/linking-api';
import {
    createKmPostFeatures,
    findMatchingKmPosts,
    getKmPostStepByResolution,
} from '../utils/km-post-layer-utils';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { filterNotEmpty } from 'utils/array-utils';

let newestLayerId = 0;

export function createGeometryKmPostLayer(
    resolution: number,
    existingOlLayer: VectorLayer<VectorSource<OlPoint | Rectangle>> | undefined,
    selection: Selection,
    publishType: PublishType,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource, style: null });

    const step = getKmPostStepByResolution(resolution);

    let inFlight = false;
    if (step) {
        inFlight = true;
        const isSelected = (kmPost: LayoutKmPost) => {
            return selection.selectedItems.geometryKmPostIds.some(
                ({ geometryId }) => geometryId === kmPost.sourceId,
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
            .catch(() => clearFeatures(vectorSource))
            .finally(() => {
                inFlight = false;
            });
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'geometry-km-post-layer',
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => {
            return {
                geometryKmPostIds: findMatchingKmPosts(hitArea, vectorSource, options)
                    .map((kp) =>
                        kp.kmPost.sourceId && kp.planId
                            ? {
                                  geometryId: kp.kmPost.sourceId,
                                  planId: kp.planId,
                              }
                            : undefined,
                    )
                    .filter(filterNotEmpty),
            };
        },
        requestInFlight: () => inFlight,
    };
}
