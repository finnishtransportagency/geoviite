import Feature from 'ol/Feature';
import { Style } from 'ol/style';
import { Point as OlPoint } from 'ol/geom';
import { MapLayerName } from 'map/map-model';
import { MapLayer } from 'map/layers/utils/layer-model';
import { LinkingSwitch, SuggestedSwitch } from 'linking/linking-model';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import {
    getLinkingJointRenderer,
    suggestedSwitchHasMatchOnJoint,
} from 'map/layers/utils/switch-layer-utils';

function createSwitchFeatures(suggestedSwitch: SuggestedSwitch): Feature<OlPoint>[] {
    const features: Feature<OlPoint>[] = [];

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

    return features;
}

const layerName: MapLayerName = 'switch-linking-layer';

export function createSwitchLinkingLayer(
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    linkingState: LinkingSwitch | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise = Promise.resolve(linkingState?.suggestedSwitch);

    const createFeatures = (suggestedSwitch: SuggestedSwitch) =>
        suggestedSwitch === undefined ? [] : createSwitchFeatures(suggestedSwitch);

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return {
        name: layerName,
        layer,
    };
}

const SUGGESTED_SWITCH_FEATURE_DATA_PROPERTY = 'suggested-switch-data';

function setSuggestedSwitchFeatureProperty(feature: Feature<OlPoint>, data: SuggestedSwitch) {
    feature.set(SUGGESTED_SWITCH_FEATURE_DATA_PROPERTY, data);
}
