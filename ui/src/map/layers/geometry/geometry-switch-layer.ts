import { Point as OlPoint } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { GeometryPlanLayout, LayoutSwitch, PlanAndStatus } from 'track-layout/track-layout-model';
import {
    createLayer,
    GeoviiteMapLayer,
    getManualPlanWithStatus,
    getVisiblePlansWithStatus,
    loadLayerData,
} from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { LayoutContext, SwitchStructure } from 'common/common-model';
import { getSwitchStructures } from 'common/common-api';
import {
    createGeometrySwitchFeatures,
    findMatchingSwitches,
} from 'map/layers/utils/switch-layer-utils';
import { Rectangle } from 'model/geometry';
import { filterNotEmpty } from 'utils/array-utils';
import { ChangeTimes } from 'common/common-slice';
import { MapLayerName, MapTile } from 'map/map-model';

const layerName: MapLayerName = 'geometry-switch-layer';

export function createGeometrySwitchLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    selection: Selection,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    resolution: number,
    manuallySetPlan: GeometryPlanLayout | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

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
        ? getManualPlanWithStatus(manuallySetPlan, layoutContext)
        : getVisiblePlansWithStatus(selection.visiblePlans, mapTiles, layoutContext, changeTimes);
    const dataPromise = Promise.all([getSwitchStructures(), plansPromise]);

    const createFeatures = ([switchStructures, planStatuses]: [
        SwitchStructure[],
        PlanAndStatus[],
    ]) => {
        return planStatuses.flatMap(({ status, plan }) =>
            createGeometrySwitchFeatures(
                status,
                visibleSwitches,
                plan,
                isSelected,
                isHighlighted,
                showLargeSymbols,
                showLabels,
                switchStructures,
            ),
        );
    };

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    const searchItems = (
        hitArea: Rectangle,
        options: SearchItemsOptions,
    ): LayerItemSearchResult => ({
        geometrySwitchIds: findMatchingSwitches(hitArea, source, options)
            .map((s) =>
                s.planId && s.switch.sourceId
                    ? {
                          geometryId: s.switch.sourceId,
                          planId: s.planId,
                      }
                    : undefined,
            )
            .filter(filterNotEmpty),
    });

    return { name: layerName, layer: layer, searchItems: searchItems };
}
