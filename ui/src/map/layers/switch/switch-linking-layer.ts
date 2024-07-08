import Feature from 'ol/Feature';
import { Style } from 'ol/style';
import { Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { LinkingSwitch, SuggestedSwitch } from 'linking/linking-model';
import { getSuggestedSwitchesByTile } from 'linking/linking-api';
import {
    createLayer,
    findMatchingEntities,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { Selection } from 'selection/selection-model';
import {
    getLinkingJointRenderer,
    suggestedSwitchHasMatchOnJoint,
} from 'map/layers/utils/switch-layer-utils';
import { SUGGESTED_SWITCH_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { filterNotEmpty, first } from 'utils/array-utils';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { LayoutContext } from 'common/common-model';

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
    mapTiles: MapTile[],
    resolution: number,
    existingOlLayer: VectorLayer<Feature<OlPoint>> | undefined,
    layoutContext: LayoutContext,
    selection: Selection,
    linkingState: LinkingSwitch | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const selectedSwitches = selection.selectedItems.suggestedSwitches;

    const getSuggestedSwitchesPromises =
        resolution <= SUGGESTED_SWITCH_SHOW && linkingState
            ? []
            : mapTiles.map((tile) => getSuggestedSwitchesByTile(layoutContext.branch, tile));

    const dataPromise: Promise<SuggestedSwitch[]> = Promise.all(getSuggestedSwitchesPromises).then(
        (suggestedSwitches) =>
            [
                ...suggestedSwitches.flat(),
                first(selectedSwitches), // add selected suggested switch into collection
            ].filter(filterNotEmpty),
    );

    const createFeatures = (suggestedSwitches: SuggestedSwitch[]) =>
        suggestedSwitches.flatMap((suggestedSwitch) =>
            createSwitchFeatures(
                suggestedSwitch,
                selectedSwitches.some((switchToCheck) => switchToCheck.id == suggestedSwitch.id),
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
