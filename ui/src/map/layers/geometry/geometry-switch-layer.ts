import { Point as OlPoint } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { GeometryPlanLayout, LayoutSwitch, PlanAndStatus } from 'track-layout/track-layout-model';
import {
    clearFeatures,
    getManualPlanWithStatus,
    getVisiblePlansWithStatus,
} from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { PublishType } from 'common/common-model';
import { getSwitchStructures } from 'common/common-api';
import { createSwitchFeatures, findMatchingSwitches } from 'map/layers/utils/switch-layer-utils';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { filterNotEmpty } from 'utils/array-utils';
import { ChangeTimes } from 'common/common-slice';
import { MapTile } from 'map/map-model';

let newestLayerId = 0;
export function createGeometrySwitchLayer(
    mapTiles: MapTile[],
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

    const shownSwitches = () => {
        if (resolution <= Limits.SWITCH_SHOW) {
            return manuallySetPlan
                ? manuallySetPlan.switches.map((s) => s.sourceId)
                : selection.visiblePlans.flatMap((p) => p.switches);
        } else {
            return selection.selectedItems.geometrySwitchIds.map((s) => s.geometryId);
        }
    };

    const visibleSwitches = shownSwitches();

    let inFlight = true;
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

    const plansPromise: Promise<PlanAndStatus[]> = manuallySetPlan
        ? getManualPlanWithStatus(manuallySetPlan, publishType)
        : getVisiblePlansWithStatus(selection.visiblePlans, mapTiles, publishType, changeTimes);

    Promise.all([getSwitchStructures(), plansPromise])
        .then(([switchStructures, planStatuses]) => {
            if (layerId !== newestLayerId) return;

            const features = planStatuses.flatMap(({ status, plan }) => {
                const switchLinkedStatus = status
                    ? new Map(
                          status.switches
                              .filter((s) => visibleSwitches.includes(s.id))
                              .map((switchItem) => [switchItem.id, switchItem.isLinked]),
                      )
                    : undefined;

                const isSwitchLinked = (switchItem: LayoutSwitch) =>
                    (switchItem.sourceId && switchLinkedStatus?.get(switchItem.sourceId)) || false;

                return createSwitchFeatures(
                    plan.switches.filter((s) => s.sourceId && visibleSwitches.includes(s.sourceId)),
                    isSelected,
                    isHighlighted,
                    isSwitchLinked,
                    showLargeSymbols,
                    showLabels,
                    plan.id,
                    switchStructures,
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
