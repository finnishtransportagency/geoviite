import Feature from 'ol/Feature';
import { Style } from 'ol/style';
import { Point as OlPoint } from 'ol/geom';
import { MapTile } from 'map/map-model';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { LinkingSwitch, SuggestedSwitch } from 'linking/linking-model';
import { getSuggestedSwitchesByTile } from 'linking/linking-api';
import { clearFeatures, findMatchingEntities, pointToCoords } from 'map/layers/utils/layer-utils';
import { Selection } from 'selection/selection-model';
import { endPointStyle, getLinkingJointRenderer } from 'map/layers/utils/switch-layer-utils';
import { SUGGESTED_SWITCH_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { filterNotEmpty } from 'utils/array-utils';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';

let newestLayerId = 0;

function createSwitchFeatures(
    suggestedSwitch: SuggestedSwitch,
    isSelected: boolean,
): Feature<OlPoint>[] {
    const features: Feature<OlPoint>[] = [];

    if (isSelected) {
        suggestedSwitch.joints.forEach((joint) => {
            const f = new Feature({
                geometry: new OlPoint(pointToCoords(joint.location)),
            });

            f.setStyle(
                new Style({
                    renderer: getLinkingJointRenderer(joint),
                }),
            );

            setSuggestedSwitchFeatureProperty(f, suggestedSwitch);
            features.push(f);
        });
    } else {
        const presentationJoint = suggestedSwitch.joints.find(
            (joint) => joint.number == suggestedSwitch.switchStructure.presentationJointNumber,
        );

        if (presentationJoint) {
            const f = new Feature({
                geometry: new OlPoint(pointToCoords(presentationJoint.location)),
            });

            f.setStyle(endPointStyle);
            setSuggestedSwitchFeatureProperty(f, suggestedSwitch);

            features.push(f);
        }
    }

    return features;
}

export function createSwitchLinkingLayer(
    mapTiles: MapTile[],
    resolution: number,
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    linkingState: LinkingSwitch | undefined,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    if (resolution <= SUGGESTED_SWITCH_SHOW) {
        const selectedSwitches = selection.selectedItems.suggestedSwitches;

        const getSuggestedSwitchesPromises = linkingState
            ? []
            : mapTiles.map((tile) => getSuggestedSwitchesByTile(tile));

        Promise.all(getSuggestedSwitchesPromises)
            .then((suggestedSwitches) =>
                [
                    ...suggestedSwitches.flat(),
                    selectedSwitches[0], // add selected suggested switch into collection
                ].filter(filterNotEmpty),
            )
            .then((suggestedSwitches) => {
                // Handle latest fetch only
                if (layerId !== newestLayerId) return;

                const features = suggestedSwitches.flatMap((suggestedSwitch) =>
                    createSwitchFeatures(
                        suggestedSwitch,
                        selectedSwitches.some(
                            (switchToCheck) => switchToCheck.id == suggestedSwitch.id,
                        ),
                    ),
                );

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'switch-linking-layer',
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => {
            return {
                suggestedSwitches: findMatchingSwitches(hitArea, vectorSource, options),
            };
        },
    };
}

const SUGGESTED_SWITCH_FEATURE_DATA_PROPERTY = 'suggested-switch-data';

function findMatchingSwitches(
    hitArea: Rectangle,
    source: VectorSource,
    options: SearchItemsOptions,
): SuggestedSwitch[] {
    return findMatchingEntities<SuggestedSwitch>(
        hitArea,
        source,
        SUGGESTED_SWITCH_FEATURE_DATA_PROPERTY,
        options,
    );
}

function setSuggestedSwitchFeatureProperty(feature: Feature<OlPoint>, data: SuggestedSwitch) {
    feature.set(SUGGESTED_SWITCH_FEATURE_DATA_PROPERTY, data);
}
