import { Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { LayoutSwitch, LayoutSwitchId } from 'track-layout/track-layout-model';
import { getSwitches, getSwitchesByTile } from 'track-layout/layout-switch-api';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import { MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { createSwitchFeatures, findMatchingSwitches } from 'map/layers/utils/switch-layer-utils';
import { PublishType } from 'common/common-model';
import { getSwitchStructures } from 'common/common-api';
import { ChangeTimes } from 'common/common-slice';
import { filterUniqueById } from 'utils/array-utils';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { fromExtent } from 'ol/geom/Polygon';
import { SplittingState } from 'tool-panel/location-track/split-store';

let shownSwitchesCompare: string;
let newestLayerId = 0;

export function createSwitchLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    splittingState: SplittingState | undefined,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged: (items: OptionalShownItems) => void,
): MapLayer {
    const layerId = ++newestLayerId;
    const getSwitchesFromApi = async () => {
        if (splittingState && resolution <= Limits.HIGHLIGHTS_SHOW) {
            const switchIds =
                splittingState?.allowedSwitches
                    .map((sw) => sw.switchId)
                    .concat(splittingState?.startAndEndSwitches) || [];

            const switches = await getSwitches(switchIds, publishType);
            return switches.filter((sw_1) => sw_1.stateCategory !== 'NOT_EXISTING');
        } else if (resolution <= Limits.SWITCH_SHOW) {
            const switchGroups = await Promise.all(
                mapTiles.map((t) => getSwitchesByTile(changeTimes.layoutSwitch, t, publishType)),
            );
            return switchGroups.flat().filter(filterUniqueById((s) => s.id));
        } else {
            return getSwitches(selection.selectedItems.switches, publishType);
        }
    };

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    function updateShownSwitches(switchIds: LayoutSwitchId[]) {
        const compare = switchIds.sort().join();

        if (compare !== shownSwitchesCompare) {
            shownSwitchesCompare = compare;
            onViewContentChanged({ switches: switchIds });
        }
    }

    let inFlight = true;
    const resolution = olView.getResolution() || 0;

    Promise.all([getSwitchesFromApi(), getSwitchStructures()])
        .then(([switches, switchStructures]) => {
            if (layerId !== newestLayerId) return;

            const largeSymbols = resolution <= Limits.SWITCH_LARGE_SYMBOLS;
            const showLabels = resolution <= Limits.SWITCH_LABELS;
            const isSelected = (switchItem: LayoutSwitch) => {
                return selection.selectedItems.switches.some((s) => s === switchItem.id);
            };

            const isHighlighted = (switchItem: LayoutSwitch) => {
                return selection.highlightedItems.switches.some((s) => s === switchItem.id);
            };

            const features = createSwitchFeatures(
                switches,
                isSelected,
                isHighlighted,
                () => false,
                largeSymbols,
                showLabels,
                undefined,
                switchStructures,
            );

            clearFeatures(vectorSource);
            vectorSource.addFeatures(features);

            const visibleSwitches = findMatchingSwitches(
                fromExtent(olView.calculateExtent()),
                vectorSource,
                {},
            ).map((s) => s.switch.id);
            updateShownSwitches(visibleSwitches);
        })
        .catch(() => {
            if (layerId === newestLayerId) {
                clearFeatures(vectorSource);
                updateShownSwitches([]);
            }
        })
        .finally(() => {
            inFlight = false;
        });

    return {
        name: 'switch-layer',
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions) => {
            const switches = findMatchingSwitches(hitArea, vectorSource, options).map(
                (d) => d.switch.id,
            );

            return { switches };
        },
        onRemove: () => updateShownSwitches([]),
        requestInFlight: () => inFlight,
    };
}
