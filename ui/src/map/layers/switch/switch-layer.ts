import OlPoint from 'ol/geom/Point';
import OlPolygon from 'ol/geom/Polygon';
import { Vector as VectorLayer } from 'ol/layer';
import OlView from 'ol/View';
import { Vector as VectorSource } from 'ol/source';
import { MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { LayoutSwitch, LayoutSwitchId } from 'track-layout/track-layout-model';
import { getSwitchesByTile } from 'track-layout/layout-switch-api';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import { MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { createSwitchFeatures, findMatchingSwitches } from 'map/layers/utils/switch-layer-utils';
import { PublishType } from 'common/common-model';
import { getSwitchStructures } from 'common/common-api';
import { ChangeTimes } from 'common/common-slice';
import { filterUniqueById } from 'utils/array-utils';

let shownSwitchesCompare: string;
let newestLayerId = 0;

export function createSwitchLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged: (items: OptionalShownItems) => void,
): MapLayer {
    const layerId = ++newestLayerId;
    const getSwitchesFromApi = () => {
        return Promise.all(
            mapTiles.map((t) => getSwitchesByTile(changeTimes.layoutSwitch, t, publishType)),
        ).then((switchGroups) => switchGroups.flat().filter(filterUniqueById((s) => s.id)));
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

    const resolution = olView.getResolution() || 0;
    if (resolution <= Limits.SWITCH_SHOW) {
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

                updateShownSwitches(switches.map((s) => s.id));
            })
            .catch(() => {
                clearFeatures(vectorSource);
                updateShownSwitches([]);
            });
    } else {
        clearFeatures(vectorSource);
        updateShownSwitches([]);
    }

    return {
        name: 'switch-layer',
        layer: layer,
        searchItems: (hitArea: OlPolygon, options: SearchItemsOptions) => {
            const switches = findMatchingSwitches(hitArea, vectorSource, options).map(
                (d) => d.switch.id,
            );

            return { switches };
        },
        onRemove: () => updateShownSwitches([]),
    };
}
