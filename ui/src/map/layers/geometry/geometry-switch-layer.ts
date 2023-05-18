import OlPoint from 'ol/geom/Point';
import OlPolygon from 'ol/geom/Polygon';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Selection } from 'selection/selection-model';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import { getMatchingSwitches } from 'map/layers/utils/layer-utils';
import { MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { PublishType } from 'common/common-model';
import { GeometryPlanId } from 'geometry/geometry-model';
import { getPlanLinkStatus } from 'linking/linking-api';
import { getSwitchStructures } from 'common/common-api';
import { createFeatures } from 'map/layers/switch/switch-layer-utils';

let newestGeometrySwitchLayerId = 0;
export function createGeometrySwitchLayer(
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    resolution: number,
): MapLayer {
    const layerId = ++newestGeometrySwitchLayerId;
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    if (resolution <= Limits.SWITCH_SHOW) {
        const largeSymbols = resolution <= Limits.SWITCH_LARGE_SYMBOLS;
        const labels = resolution <= Limits.SWITCH_LABELS;
        const isSelected = (switchItem: LayoutSwitch) => {
            return selection.selectedItems.geometrySwitches.some(
                (s) => s.geometryItem.id === switchItem.id,
            );
        };

        const isHighlighted = (switchItem: LayoutSwitch) => {
            return selection.highlightedItems.geometrySwitches.some(
                (s) => s.geometryItem.id === switchItem.id,
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

        Promise.all([getSwitchStructures(), ...planStatusPromises])
            .then(([switchStructures, ...statusResults]) => {
                if (layerId != newestGeometrySwitchLayerId) return;

                const features = statusResults.flatMap((statusResult) => {
                    const plan = statusResult.plan;
                    const switchLinkedStatus = statusResult.status
                        ? new Map(
                              statusResult.status.switches.map((switchItem) => [
                                  switchItem.id,
                                  switchItem.isLinked,
                              ]),
                          )
                        : undefined;

                    const isSwitchLinked = (switchItem: LayoutSwitch) =>
                        (switchItem.sourceId && switchLinkedStatus?.get(switchItem.sourceId)) ||
                        false;

                    return createFeatures(
                        plan.switches,
                        isSelected,
                        isHighlighted,
                        isSwitchLinked,
                        largeSymbols,
                        labels,
                        plan.planId,
                        switchStructures,
                    );
                });

                vectorSource.clear();
                vectorSource.addFeatures(features);
            })
            .catch(vectorSource.clear);
    }

    return {
        name: 'geometry-switch-layer',
        layer: layer,
        searchItems: (hitArea: OlPolygon, options: SearchItemsOptions) => {
            const switches = getMatchingSwitches(
                hitArea,
                vectorSource.getFeaturesInExtent(hitArea.getExtent()),
                {
                    strategy: options.limit == 1 ? 'nearest' : 'limit',
                    limit: options.limit,
                },
            ).map((d) => {
                return {
                    geometryItem: d.switch,
                    planId: d.planId as GeometryPlanId,
                };
            });

            return {
                geometrySwitches: switches,
            };
        },
    };
}
