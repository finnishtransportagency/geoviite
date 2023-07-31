import { Point as OlPoint } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { GeometryPlanLayout, LayoutSwitch, PlanAndStatus } from 'track-layout/track-layout-model';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { PublishType } from 'common/common-model';
import { getPlanLinkStatus } from 'linking/linking-api';
import { getSwitchStructures } from 'common/common-api';
import { createSwitchFeatures, findMatchingSwitches } from 'map/layers/utils/switch-layer-utils';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { filterNotEmpty } from 'utils/array-utils';
import { ChangeTimes } from 'common/common-slice';
import { getTrackLayoutPlan } from 'geometry/geometry-api';

let newestLayerId = 0;
export function createGeometrySwitchLayer(
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
    manuallySetPlan?: GeometryPlanLayout,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    const visibleSwitches = manuallySetPlan
        ? manuallySetPlan.switches.map((s) => s.sourceId)
        : selection.visiblePlans.flatMap((p) => p.switches);

    let inFlight = false;
    if (resolution <= Limits.SWITCH_SHOW) {
        inFlight = true;
        const showLargeSymbols = resolution <= Limits.SWITCH_LARGE_SYMBOLS;
        const showLabels = resolution <= Limits.SWITCH_LABELS;
        const isSelected = (switchItem: LayoutSwitch) => {
            return selection.selectedItems.geometrySwitchIds.some(
                ({ geometryId }) => geometryId === switchItem.sourceId,
            );
        };

        const isHighlighted = (switchItem: LayoutSwitch) => {
            return selection.highlightedItems.geometrySwitchIds.some(
                ({ geometryId }) => geometryId === switchItem.sourceId,
            );
        };

        // TODO: GVT-826 This section is identical in all layers: move to common util
        const planLayoutsPromises = manuallySetPlan
            ? [Promise.resolve(manuallySetPlan)]
            : selection.visiblePlans.map((p) =>
                  getTrackLayoutPlan(p.id, changeTimes.geometryPlan, true),
              );
        const planStatusPromises: Promise<PlanAndStatus | undefined>[] = planLayoutsPromises.map(
            (planPromise) =>
                planPromise.then((plan) => {
                    if (!plan) return undefined;
                    else if (plan.planDataType == 'TEMP') return { plan, status: undefined };
                    else
                        return getPlanLinkStatus(plan.planId, publishType).then((status) => ({
                            plan,
                            status,
                        }));
                }),
        );

        Promise.all([getSwitchStructures(), ...planStatusPromises])
            .then(([switchStructures, ...planStatuses]) => {
                if (layerId !== newestLayerId) return;

                const features = planStatuses.filter(filterNotEmpty).flatMap(({ status, plan }) => {
                    const switchLinkedStatus = status
                        ? new Map(
                              status.switches
                                  .filter((s) => visibleSwitches.includes(s.id))
                                  .map((switchItem) => [switchItem.id, switchItem.isLinked]),
                          )
                        : undefined;

                    const isSwitchLinked = (switchItem: LayoutSwitch) =>
                        (switchItem.sourceId && switchLinkedStatus?.get(switchItem.sourceId)) ||
                        false;

                    return createSwitchFeatures(
                        plan.switches.filter(
                            (s) => s.sourceId && visibleSwitches.includes(s.sourceId),
                        ),
                        isSelected,
                        isHighlighted,
                        isSwitchLinked,
                        showLargeSymbols,
                        showLabels,
                        plan.planId,
                        switchStructures,
                    );
                });

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            })
            .catch(() => {
                clearFeatures(vectorSource);
            })
            .finally(() => {
                inFlight = false;
            });
    } else {
        vectorSource.clear();
    }

    return {
        name: 'geometry-switch-layer',
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => {
            return {
                geometrySwitchIds: findMatchingSwitches(hitArea, vectorSource, options)
                    .map((s) =>
                        s.planId && s.switch.sourceId
                            ? {
                                  geometryId: s.switch.sourceId,
                                  planId: s.planId,
                              }
                            : undefined,
                    )
                    .filter(filterNotEmpty),
            };
        },
        requestInFlight: () => inFlight,
    };
}
