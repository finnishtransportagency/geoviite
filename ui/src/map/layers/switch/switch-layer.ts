import { Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapLayerName, MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { LayoutSwitch, LayoutSwitchId } from 'track-layout/track-layout-model';
import {
    getSwitches,
    getSwitchesByTile,
    getSwitchesValidation,
    getSwitchesValidationByTile,
} from 'track-layout/layout-switch-api';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import {
    createLayoutSwitchFeatures,
    findMatchingSwitches,
} from 'map/layers/utils/switch-layer-utils';
import { LayoutContext, SwitchStructure, TimeStamp } from 'common/common-model';
import { getSwitchStructures } from 'common/common-api';
import { ChangeTimes } from 'common/common-slice';
import { filterUniqueById } from 'utils/array-utils';
import { Rectangle } from 'model/geometry';
import { fromExtent } from 'ol/geom/Polygon';
import { getAllowedSwitchesFromState, SplittingState } from 'tool-panel/location-track/split-store';
import { getMaxTimestamp } from 'utils/date-utils';
import { ValidatedSwitch } from 'publication/publication-model';

let shownSwitchesCompare: string;

const getTiledSwitchValidation = (
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
    switchChangeTime: TimeStamp,
) =>
    Promise.all(
        mapTiles.map((tile) => getSwitchesValidationByTile(switchChangeTime, tile, layoutContext)),
    ).then((results) => results.flat());

type SwitchLayerData = {
    switches: LayoutSwitch[];
    structures: SwitchStructure[];
    validationResult: ValidatedSwitch[];
    disabled: boolean;
};

const layerName: MapLayerName = 'switch-layer';

export function createSwitchLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    selection: Selection,
    splittingState: SplittingState | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged: (items: OptionalShownItems) => void,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const resolution = olView.getResolution() || 0;

    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const allowedSwitches = splittingState ? getAllowedSwitchesFromState(splittingState) : [];

    const getSwitchesFromApi = async () => {
        if (splittingState && resolution <= Limits.HIGHLIGHTS_SHOW) {
            const switchIds = allowedSwitches.map((sw) => sw.switchId) || [];

            const switches = await getSwitches(switchIds, layoutContext);
            return switches.filter((sw_1) => sw_1.stateCategory !== 'NOT_EXISTING');
        } else if (resolution <= Limits.SWITCH_SHOW) {
            const switchGroups = await Promise.all(
                mapTiles.map((t) => getSwitchesByTile(changeTimes.layoutSwitch, t, layoutContext)),
            );
            return switchGroups.flat().filter(filterUniqueById((s) => s.id));
        } else {
            return getSwitches(selection.selectedItems.switches, layoutContext);
        }
    };

    const getSwitchValidation = () => {
        if (splittingState && resolution <= Limits.HIGHLIGHTS_SHOW) {
            const switchIds = allowedSwitches.map((sw) => sw.switchId) || [];

            return getSwitchesValidation(layoutContext, switchIds);
        } else if (resolution <= Limits.SWITCH_SHOW) {
            return getTiledSwitchValidation(
                mapTiles,
                layoutContext,
                getMaxTimestamp(changeTimes.layoutSwitch, changeTimes.layoutLocationTrack),
            ).then((tile) => tile.flat());
        } else {
            return getSwitchesValidation(layoutContext, selection.selectedItems.switches);
        }
    };

    const dataPromise: Promise<SwitchLayerData> = Promise.all([
        getSwitchesFromApi(),
        getSwitchStructures(),
        getSwitchValidation(),
    ]).then(([switches, structures, validationResult]) => ({
        switches,
        structures,
        validationResult,
        disabled: splittingState?.disabled ?? false,
    }));

    function updateShownSwitches(switchIds: LayoutSwitchId[]) {
        const compare = switchIds.sort().join();

        if (compare !== shownSwitchesCompare) {
            shownSwitchesCompare = compare;
            onViewContentChanged({ switches: switchIds });
        }
    }

    const createFeatures = (data: SwitchLayerData) =>
        createLayoutSwitchFeatures(
            resolution,
            selection,
            data.switches,
            data.disabled,
            data.structures,
            data.validationResult,
        );
    const onLoadingChange = (loading: boolean) => {
        if (!loading) {
            // The filtering should be as tight as possible for busy track-yards.
            // This only shows switches whose presentation joint is in the view.
            const visibleSwitches = findMatchingSwitches(
                fromExtent(olView.calculateExtent()),
                source,
                {},
            ).map((s) => s.switch.id);
            updateShownSwitches(visibleSwitches);
        }
        onLoadingData(loading);
    };
    loadLayerData(source, isLatest, onLoadingChange, dataPromise, createFeatures);

    return {
        name: layerName,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions) => ({
            switches: !splittingState?.disabled
                ? findMatchingSwitches(hitArea, source, options).map((d) => d.switch.id)
                : [],
        }),
        onRemove: () => updateShownSwitches([]),
    };
}
