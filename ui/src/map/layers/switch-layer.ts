import OlPoint from 'ol/geom/Point';
import OlPolygon, { fromExtent } from 'ol/geom/Polygon';
import { Vector as VectorLayer } from 'ol/layer';
import OlView from 'ol/View';
import { Vector as VectorSource } from 'ol/source';
import { MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { LayoutSwitch, LayoutSwitchId } from 'track-layout/track-layout-model';
import { getSwitchesByTile } from 'track-layout/layout-switch-api';
import { getMatchingSwitches } from 'map/layers/layer-utils';
import { MapLayer, SearchItemsOptions } from 'map/layers/layer-model';
import * as Limits from 'map/layers/layer-visibility-limits';
import { createFeatures } from 'map/layers/switch-layer-utils';
import { PublishType, TimeStamp } from 'common/common-model';
import { getSwitchStructures } from 'common/common-api';
import { ChangeTimes } from 'common/common-slice';

let switchIdCompare = '';
let switchChangeTimeCompare: TimeStamp | undefined = undefined;
let newestSwitchLayerId = 0;

export function createSwitchLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged?: (items: OptionalShownItems) => void,
): MapLayer {
    const layerId = ++newestSwitchLayerId;
    const getSwitchesFromApi = () => {
        return Promise.all(
            mapTiles.map((t) => getSwitchesByTile(changeTimes.layoutSwitch, t, publishType)),
        ).then((switchGroups) => [...new Set(switchGroups.flatMap((switches) => switches))]);
    };

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
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

        return {
            switches: switches,
        };
    };

    const switchesChanged = (newIds: LayoutSwitchId[]) => {
        if (onViewContentChanged) {
            const newCompare = `${publishType}${JSON.stringify(newIds.sort())}`;
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

    const clearFeatures = () => {
        vectorSource.clear();
    };

    const resolution = olView.getResolution() || 0;
    if (resolution <= Limits.SWITCH_SHOW) {
        Promise.all([getSwitchesFromApi(), getSwitchStructures()])
            .then(([switches, switchStructures]) => {
                if (layerId != newestSwitchLayerId) return;

                const largeSymbols = resolution <= Limits.SWITCH_LARGE_SYMBOLS;
                const labels = resolution <= Limits.SWITCH_LABELS;
                const isSelected = (switchItem: LayoutSwitch) => {
                    return selection.selectedItems.switches.some((s) => s === switchItem.id);
                };

                const isHighlighted = (switchItem: LayoutSwitch) => {
                    return selection.highlightedItems.switches.some((s) => s === switchItem.id);
                };

                clearFeatures();
                vectorSource.addFeatures(
                    createFeatures(
                        switches,
                        isSelected,
                        isHighlighted,
                        () => false,
                        largeSymbols,
                        labels,
                        undefined,
                        switchStructures,
                    ),
                );
                switchesChanged(switches.map((s) => s.id));
            })
            .catch(clearFeatures);
    } else {
        clearFeatures();
        switchesChanged([]);
    }

    return {
        name: 'switch-layer',
        layer: layer,
        searchItems: searchFunction,
        searchShownItems: searchFunction,
    };
}
