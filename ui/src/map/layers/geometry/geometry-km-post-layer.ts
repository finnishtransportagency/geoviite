import { Point as OlPoint } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { GeometryPlanLayout, LayoutKmPost, PlanAndStatus } from 'track-layout/track-layout-model';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import {
    clearFeatures,
    getManualPlanWithStatus,
    getVisiblePlansWithStatus,
} from 'map/layers/utils/layer-utils';
import { PublishType } from 'common/common-model';
import {
    createKmPostFeatures,
    findMatchingKmPosts,
    getKmPostStepByResolution,
} from '../utils/km-post-layer-utils';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { filterNotEmpty } from 'utils/array-utils';
import { ChangeTimes } from 'common/common-slice';

let newestLayerId = 0;

export function createGeometryKmPostLayer(
    resolution: number,
    existingOlLayer: VectorLayer<VectorSource<OlPoint | Rectangle>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    manuallySetPlan?: GeometryPlanLayout,
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

        const visibleKmPosts = manuallySetPlan
            ? manuallySetPlan.kmPosts.map((p) => p.sourceId)
            : selection.visiblePlans.flatMap((p) => p.kmPosts);

        const plansPromise: Promise<PlanAndStatus[]> = manuallySetPlan
            ? getManualPlanWithStatus(manuallySetPlan, publishType)
            : getVisiblePlansWithStatus(selection.visiblePlans, publishType, changeTimes);

        plansPromise
            .then((planStatuses) => {
                if (layerId !== newestLayerId) return;

                const features = planStatuses.flatMap(({ plan, status }) => {
                    const kmPosts: LayoutKmPost[] = plan.kmPosts.filter(
                        ({ sourceId, kmNumber }) =>
                            sourceId &&
                            visibleKmPosts.includes(sourceId) &&
                            Number.parseInt(kmNumber) % step === 0,
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
            .catch(() => {
                if (layerId === newestLayerId) clearFeatures(vectorSource);
            })
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
