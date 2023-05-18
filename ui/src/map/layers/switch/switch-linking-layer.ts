import Feature from 'ol/Feature';
import { Style } from 'ol/style';
import { Point, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile } from 'map/map-model';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { LinkingSwitch, SuggestedSwitch } from 'linking/linking-model';
import { getSuggestedSwitchesByTile } from 'linking/linking-api';
import {
    clearFeatures,
    getMatchingSuggestedSwitches,
    MatchOptions,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { Selection } from 'selection/selection-model';
import { endPointStyle, getLinkingJointRenderer } from 'map/layers/switch/switch-layer-utils';
import { SUGGESTED_SWITCH_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { filterNotEmpty, flatten } from 'utils/array-utils';

export const FEATURE_PROPERTY_SUGGESTED_SWITCH = 'suggested-switch';

let newestLayerId = 0;

function createFeatures(suggestedSwitch: SuggestedSwitch, isSelected: boolean): Feature<Point>[] {
    const features: Feature<Point>[] = [];

    if (isSelected) {
        suggestedSwitch.joints.forEach((joint) => {
            const f = new Feature({
                geometry: new Point(pointToCoords(joint.location)),
            });

            f.setStyle(
                new Style({
                    renderer: getLinkingJointRenderer(joint),
                }),
            );

            f.set(FEATURE_PROPERTY_SUGGESTED_SWITCH, suggestedSwitch);
            features.push(f);
        });
    } else {
        const presentationJoint = suggestedSwitch.joints.find(
            (joint) => joint.number == suggestedSwitch.switchStructure.presentationJointNumber,
        );

        if (presentationJoint) {
            const f = new Feature({
                geometry: new Point(pointToCoords(presentationJoint.location)),
            });

            f.setStyle(endPointStyle);
            f.set(FEATURE_PROPERTY_SUGGESTED_SWITCH, suggestedSwitch);

            features.push(f);
        }
    }

    return features;
}

export function createSwitchLinkingLayer(
    mapTiles: MapTile[],
    resolution: number,
    existingOlLayer: VectorLayer<VectorSource<Point>> | undefined,
    selection: Selection,
    linkingState: LinkingSwitch | undefined,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    function updateFeatures(features: Feature<Point>[]) {
        clearFeatures(vectorSource);
        vectorSource.addFeatures(features);
    }

    if (resolution <= SUGGESTED_SWITCH_SHOW) {
        const selectedSwitches = selection.selectedItems.suggestedSwitches;

        const getSuggestedSwitchesPromises = linkingState
            ? []
            : mapTiles.map((tile) => getSuggestedSwitchesByTile(tile));

        Promise.all(getSuggestedSwitchesPromises)
            .then(flatten)
            .then((suggestedSwitches) =>
                [
                    ...suggestedSwitches,
                    selectedSwitches[0], // add selected suggested switch into collection
                ].filter(filterNotEmpty),
            )
            .then((suggestedSwitches) => {
                // Handle latest fetch only
                if (layerId !== newestLayerId) return;

                const features = suggestedSwitches.flatMap((suggestedSwitch) =>
                    createFeatures(
                        suggestedSwitch,
                        selectedSwitches.some(
                            (switchToCheck) => switchToCheck.id == suggestedSwitch.id,
                        ),
                    ),
                );
                updateFeatures(features);
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'linking-switch-layer',
        layer: layer,
        searchItems: (hitArea: Polygon, options: SearchItemsOptions): LayerItemSearchResult => {
            const matchOptions: MatchOptions = {
                strategy: options.limit == 1 ? 'nearest' : 'limit',
                limit: options.limit,
            };
            const features = vectorSource.getFeaturesInExtent(hitArea.getExtent());

            return {
                suggestedSwitches: getMatchingSuggestedSwitches(hitArea, features, matchOptions),
            };
        },
    };
}
