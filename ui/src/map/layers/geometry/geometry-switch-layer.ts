import { Point as OlPoint } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { LayoutSwitch } from 'track-layout/track-layout-model';
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

let newestLayerId = 0;
export function createGeometrySwitchLayer(
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

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

        const planStatusPromises = selection.planLayouts.map((plan) =>
            plan.planDataType == 'STORED'
                ? getPlanLinkStatus(plan.planId, publishType).then((status) => ({ plan, status }))
                : { plan, status: undefined },
        );

        Promise.all([getSwitchStructures(), ...planStatusPromises])
            .then(([switchStructures, ...planStatuses]) => {
                if (layerId !== newestLayerId) return;

                const features = planStatuses.flatMap(({ status, plan }) => {
                    const switchLinkedStatus = status
                        ? new Map(
                              status.switches.map((switchItem) => [
                                  switchItem.id,
                                  switchItem.isLinked,
                              ]),
                          )
                        : undefined;

                    const isSwitchLinked = (switchItem: LayoutSwitch) =>
                        (switchItem.sourceId && switchLinkedStatus?.get(switchItem.sourceId)) ||
                        false;

                    return createSwitchFeatures(
                        plan.switches,
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
