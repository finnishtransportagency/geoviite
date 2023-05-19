import OlPoint from 'ol/geom/Point';
import OlPolygon, { fromExtent } from 'ol/geom/Polygon';
import { Vector as VectorLayer } from 'ol/layer';
import OlView from 'ol/View';
import { Vector as VectorSource } from 'ol/source';
import { MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { LayoutSwitch, LayoutSwitchId } from 'track-layout/track-layout-model';
import { getSwitchesByTile } from 'track-layout/layout-switch-api';
import { clearFeatures, getMatchingSwitches } from 'map/layers/utils/layer-utils';
import { MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { createFeatures } from 'map/layers/switch/switch-layer-utils';
import { PublishType, TimeStamp } from 'common/common-model';
import { getSwitchStructures } from 'common/common-api';
import { ChangeTimes } from 'common/common-slice';

let switchIdCompare = '';
let switchChangeTimeCompare: TimeStamp | undefined = undefined;
let newestLayerId = 0;

export function createSwitchLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged?: (items: OptionalShownItems) => void,
): MapLayer {
    const layerId = ++newestLayerId;
    const getSwitchesFromApi = () => {
        return Promise.all(
            mapTiles.map((t) => getSwitchesByTile(changeTimes.layoutSwitch, t, publishType)),
        ).then((switchGroups) => [...new Set(switchGroups.flatMap((switches) => switches))]);
    };

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    const searchFunction = (hitArea: OlPolygon, options: SearchItemsOptions) => {
        const switches = getMatchingSwitches(
            hitArea,
            vectorSource.getFeaturesInExtent(hitArea.getExtent()),
            {
                strategy: options.limit == 1 ? 'nearest' : 'limit',
                limit: options.limit,
            },
        ).map((d) => d.switch.id);

        return { switches };
    };

    const switchesChanged = (newIds: LayoutSwitchId[]) => {
        if (onViewContentChanged) {
            const newCompare = `${publishType}${newIds.sort().join()}`;
            const changeTimeCompare = changeTimes.layoutSwitch;
            if (newCompare !== switchIdCompare || changeTimeCompare !== switchChangeTimeCompare) {
                switchIdCompare = newCompare;
                switchChangeTimeCompare = changeTimeCompare;
                const area = fromExtent(olView.calculateExtent());
                const result = searchFunction(area, {});
                onViewContentChanged(result);
            }
        }
    };

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

                const features = createFeatures(
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

                switchesChanged(switches.map((s) => s.id));
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
        switchesChanged([]);
    }

    return {
        name: 'switch-layer',
        layer: layer,
        searchItems: searchFunction,
        searchShownItems: searchFunction,
    };
}
