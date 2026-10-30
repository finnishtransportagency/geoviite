import { Point as OlPoint } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { GeometryPlanLayout, LayoutKmPost, PlanAndStatus } from 'track-layout/track-layout-model';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import {
    createLayer,
    GeoviiteMapLayer,
    getManualPlanWithStatus,
    getVisiblePlansWithStatus,
    loadLayerData,
} from 'map/layers/utils/layer-utils';
import {
    createKmPostFeatures,
    findMatchingKmPosts,
    getKmPostStepByResolution,
} from '../utils/km-post-layer-utils';
import { shouldShowKmPostAtStep } from '../utils/km-post-layer-visibility';
import { Rectangle } from 'model/geometry';
import { filterNotEmpty } from 'utils/array-utils';
import { ChangeTimes } from 'common/common-slice';
import { MapLayerName, MapTile } from 'map/map-model';
import { LayoutContext } from 'common/common-model';

const layerName: MapLayerName = 'geometry-km-post-layer';

export function createGeometryKmPostLayer(
    mapTiles: MapTile[],
    resolution: number,
    existingOlLayer: GeoviiteMapLayer<OlPoint | Rectangle> | undefined,
    selection: Selection,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    manuallySetPlan: GeometryPlanLayout | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer, false);

    const step = getKmPostStepByResolution(resolution);
    const selectedGeometryKmPosts = selection.selectedItems.geometryKmPostIds;
    const selectedKmPostIds = selectedGeometryKmPosts.map(({ geometryId }) => geometryId);
    const selectedPlanIds = new Set(selectedGeometryKmPosts.map(({ planId }) => planId));
    const visiblePlans =
        step === 0
            ? selection.visiblePlans.filter(({ id }) => selectedPlanIds.has(id))
            : selection.visiblePlans;
    const shouldSkipPlanFetch =
        step === 0 &&
        (selectedGeometryKmPosts.length === 0 || (!manuallySetPlan && visiblePlans.length === 0));

    const dataPromise: Promise<PlanAndStatus[]> = shouldSkipPlanFetch
        ? Promise.resolve([])
        : manuallySetPlan
          ? getManualPlanWithStatus(manuallySetPlan, layoutContext)
          : getVisiblePlansWithStatus(visiblePlans, mapTiles, layoutContext, changeTimes);

    const createFeatures = (planStatuses: PlanAndStatus[]) => {
        const isSelected = (kmPost: LayoutKmPost) => {
            return selection.selectedItems.geometryKmPostIds.some(
                ({ geometryId }) => geometryId === kmPost.sourceId,
            );
        };
        const isHighlighted = (kmPost: LayoutKmPost) => {
            return selection.highlightedItems.geometryKmPostIds.some(
                ({ geometryId }) => geometryId === kmPost.sourceId,
            );
        };

        const visibleKmPosts =
            step === 0
                ? selectedKmPostIds
                : manuallySetPlan
                  ? manuallySetPlan.kmPosts.map((p) => p.sourceId)
                  : selection.visiblePlans.flatMap((p) => p.kmPosts);

        return planStatuses.flatMap(({ plan, status }) => {
            const kmPosts: LayoutKmPost[] = plan.kmPosts.filter(
                ({ sourceId, kmNumber }) =>
                    sourceId &&
                    visibleKmPosts.includes(sourceId) &&
                    shouldShowKmPostAtStep(kmNumber, step, selectedKmPostIds.includes(sourceId)),
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
                isHighlighted,
                'geometryKmPost',
                resolution,
                plan.id,
                (kmPost) => (kmPost.sourceId && kmPostLinkedStatus?.get(kmPost.sourceId)) || false,
            );
        });
    };

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    const searchItems = (
        hitArea: Rectangle,
        options: SearchItemsOptions,
    ): LayerItemSearchResult => ({
        geometryKmPostIds: findMatchingKmPosts(hitArea, source, options)
            .map((kp) =>
                kp.kmPost.sourceId && kp.planId
                    ? {
                          geometryId: kp.kmPost.sourceId,
                          planId: kp.planId,
                      }
                    : undefined,
            )
            .filter(filterNotEmpty),
    });

    return { name: layerName, layer: layer, searchItems: searchItems };
}
