import Feature from 'ol/Feature';
import { Style } from 'ol/style';
import { Point as OlPoint } from 'ol/geom';
import { MapLayerName } from 'map/map-model';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { LinkingSwitch, SuggestedSwitch } from 'linking/linking-model';
import {
    createLayer,
    findMatchingEntities,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { Selection } from 'selection/selection-model';
import {
    getLinkingJointRenderer,
    suggestedSwitchHasMatchOnJoint,
} from 'map/layers/utils/switch-layer-utils';
import { Rectangle } from 'model/geometry';
import VectorSource from 'ol/source/Vector';

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
                    renderer: getLinkingJointRenderer(
                        joint,
                        suggestedSwitchHasMatchOnJoint(suggestedSwitch, joint.number),
                    ),
                }),
            );

            setSuggestedSwitchFeatureProperty(f, suggestedSwitch);
            features.push(f);
        });
    }

    return features;
}

const layerName: MapLayerName = 'switch-linking-layer';

export function createSwitchLinkingLayer(
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    selection: Selection,
    linkingState: LinkingSwitch | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const selectedSwitches = selection.selectedItems.suggestedSwitches;

    const dataPromise = Promise.resolve(
        linkingState?.suggestedSwitch ? [linkingState.suggestedSwitch] : [],
    );

    const createFeatures = (suggestedSwitches: SuggestedSwitch[]) =>
        suggestedSwitches.flatMap((suggestedSwitch) =>
            createSwitchFeatures(
                suggestedSwitch,
                selectedSwitches.some((switchToCheck) => switchToCheck.id === suggestedSwitch.id),
            ),
        );

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return {
        name: layerName,
        layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => ({
            suggestedSwitches: findMatchingSwitches(hitArea, source, options),
        }),
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
