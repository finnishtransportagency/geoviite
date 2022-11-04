import Feature from 'ol/Feature';
import { Style } from 'ol/style';
import { Point, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile, SwitchLinkingLayer } from 'map/map-model';
import { LayerItemSearchResult, OlLayerAdapter, SearchItemsOptions } from 'map/layers/layer-model';
import { LinkingSwitch, SuggestedSwitch } from 'linking/linking-model';
import { getSuggestedSwitchesByTile } from 'linking/linking-api';
import { getMatchingSuggestedSwitches, MatchOptions } from 'map/layers/layer-utils';
import { Selection } from 'selection/selection-model';
import { createLinkingJointRenderer } from 'map/layers/switch-renderers';
import { endPointStyle } from 'map/layers/linking-layer';
import { SUGGESTED_SWITCH_SHOW } from 'map/layers/layer-visibility-limits';
import { filterNotEmpty } from 'utils/array-utils';

export type SwitchLinkingFeatureType = Point;

export const FEATURE_PROPERTY_SUGGESTED_SWITCH = 'suggested-switch';

function createFeatures(
    suggestedSwitch: SuggestedSwitch,
    isSelected: boolean,
): Feature<SwitchLinkingFeatureType>[] {
    const features: Feature<SwitchLinkingFeatureType>[] = [];

    if (!isSelected) {
        const presentationJoint = suggestedSwitch.joints.find(
            (joint) => joint.number == suggestedSwitch.switchStructure.presentationJointNumber,
        );

        if (presentationJoint) {
            const f = new Feature<Point>({
                geometry: new Point([presentationJoint.location.x, presentationJoint.location.y]),
            });
            f.setStyle(endPointStyle);
            f.set(FEATURE_PROPERTY_SUGGESTED_SWITCH, suggestedSwitch);
            features.push(f);
        }
    } else {
        suggestedSwitch.joints.forEach((joint) => {
            const f = new Feature<Point>({
                geometry: new Point([joint.location.x, joint.location.y]),
            });
            f.setStyle(
                new Style({
                    renderer: createLinkingJointRenderer(
                        joint,
                        joint.number == suggestedSwitch.switchStructure.presentationJointNumber,
                    ),
                    zIndex: 1,
                }),
            );
            f.set(FEATURE_PROPERTY_SUGGESTED_SWITCH, suggestedSwitch);
            features.push(f);
        });
    }

    return features;
}

const featureCache: Map<string, Feature<SwitchLinkingFeatureType>> = new Map();

export function createSwitchLinkingLayerAdapter(
    mapLayer: SwitchLinkingLayer,
    mapTiles: MapTile[],
    resolution: number | undefined,
    existingOlLayer: VectorLayer<VectorSource<SwitchLinkingFeatureType>> | undefined,
    selection: Selection,
    linkingState: LinkingSwitch | undefined,
): OlLayerAdapter {
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
    const layer: VectorLayer<VectorSource<SwitchLinkingFeatureType>> =
        existingOlLayer ||
        new VectorLayer({
            source: vectorSource,
        });

    function clearFeatures() {
        vectorSource.clear();
    }

    function updateFeatures(features: Feature<SwitchLinkingFeatureType>[]) {
        clearFeatures();
        vectorSource.addFeatures(features);
    }

    layer.setVisible(mapLayer.visible);

    if (resolution && resolution < SUGGESTED_SWITCH_SHOW) {
        const selectedSuggestedSwitch = selection.selectedItems.suggestedSwitches[0];

        const loadSuggestedSwitches = linkingState == undefined;
        const getSuggestedSwitchesPromises = loadSuggestedSwitches
            ? mapTiles.map((tile) => getSuggestedSwitchesByTile(tile))
            : [];
        const updateFeaturesPromise = Promise.all(getSuggestedSwitchesPromises)
            .then((suggestedSwitchGoups) => suggestedSwitchGoups.flat())
            .then((suggestedSwitches) =>
                [
                    ...suggestedSwitches,
                    selectedSuggestedSwitch, // add selected suggested switch into collection
                ].filter(filterNotEmpty),
            )
            .then((suggestedSwitches) => {
                return suggestedSwitches.flatMap((suggestedSwitch) =>
                    createFeatures(
                        suggestedSwitch,
                        selection.selectedItems.suggestedSwitches.some(
                            (switchToCheck) => switchToCheck.id == suggestedSwitch.id,
                        ),
                    ),
                );
            })
            .then((features) => {
                // Handle latest fetch only
                if (layer.get('updateFeaturesPromise') === updateFeaturesPromise) {
                    featureCache.clear();
                    //features.forEach(f => featureCache.set(, f));
                    return updateFeatures(features);
                }
            })
            .catch(() => clearFeatures());

        layer.set('updateFeaturesPromise', updateFeaturesPromise);
    } else {
        clearFeatures();
    }

    return {
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
