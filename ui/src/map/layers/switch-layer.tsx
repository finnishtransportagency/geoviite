import OlFeature from 'ol/Feature';
import OlPoint from 'ol/geom/Point';
import OlPolygon, { fromExtent } from 'ol/geom/Polygon';
import { Point } from 'model/geometry';
import { Vector as VectorLayer } from 'ol/layer';
import OlView from 'ol/View';
import { Vector as VectorSource } from 'ol/source';
import { Style } from 'ol/style';
import { GeometrySwitchLayer, MapTile, OptionalShownItems, SwitchLayer } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { adapterInfoRegister } from './register';
import { LayoutSwitch, LayoutSwitchId, LayoutSwitchJoint } from 'track-layout/track-layout-model';
import { getSwitchesByTile } from 'track-layout/layout-switch-api';
import { getMatchingSwitches } from 'map/layers/layer-utils';
import { OlLayerAdapter, SearchItemsOptions } from 'map/layers/layer-model';
import { calculateTileSize } from 'map/map-utils';
import * as Limits from 'map/layers/layer-visibility-limits';
import {
    createJointRenderer,
    createSelectedSwitchLabelRenderer,
    createUnselectedSwitchRenderer,
} from 'map/layers/switch-renderers';
import { ChangeTimes } from 'track-layout/track-layout-store';
import { LinkingState } from 'linking/linking-model';
import { PublishType, SwitchStructure, TimeStamp } from 'common/common-model';
import { GeometryPlanId } from 'geometry/geometry-model';
import { getPlanLinkStatus } from 'linking/linking-api';
import { getSwitchStructures } from 'common/common-api';

const switchFeatures = new Map<string, OlFeature<OlPoint>[]>();

function createFeatures(
    layoutSwitches: LayoutSwitch[],
    isSelected: (switchItem: LayoutSwitch) => boolean,
    isHighlighted: (switchItem: LayoutSwitch) => boolean,
    isLinked: (switchItem: LayoutSwitch) => boolean,
    publishType: PublishType,
    largeSymbols: boolean,
    labels: boolean,
    planId?: GeometryPlanId,
    switchStructures?: SwitchStructure[],
): OlFeature<OlPoint>[] {
    const previousSwitchFeatures = new Map<string, OlFeature<OlPoint>[]>(switchFeatures);
    switchFeatures.clear();

    return layoutSwitches
        .filter((s) => s.joints.length > 0)
        .flatMap((layoutSwitch) => {
            const selected = isSelected(layoutSwitch);
            const highlighted = isHighlighted(layoutSwitch);
            const linked = isLinked(layoutSwitch);
            const structure = switchStructures?.find(
                (structure) => structure.id === layoutSwitch.switchStructureId,
            );
            const presentationJointNumber = structure?.presentationJointNumber;

            const cacheKey = key(
                layoutSwitch.id,
                layoutSwitch.name,
                layoutSwitch.version,
                publishType === 'OFFICIAL',
                selected,
                highlighted,
                linked,
                largeSymbols,
                labels,
            );

            let features = previousSwitchFeatures.get(cacheKey);
            if (features == undefined) {
                features = createNewFeatures(
                    cacheKey,
                    layoutSwitch,
                    selected,
                    highlighted,
                    linked,
                    largeSymbols,
                    labels,
                    planId,
                    presentationJointNumber,
                );
            }
            switchFeatures.set(cacheKey, features);
            return features;
        });
}

function key(id: string, name: string, version: string | null, ...flags: boolean[]): string {
    return [id, name, version ? version : '', ...flags.map((f) => (f ? '1' : '0'))].join('_');
}

function createNewFeatures(
    id: string,
    layoutSwitch: LayoutSwitch,
    selected: boolean,
    highlighted: boolean,
    linked: boolean,
    largeSymbols: boolean,
    labels: boolean,
    planId?: GeometryPlanId,
    presentationJointNumber?: string | undefined,
) {
    const presentationJoint = presentationJointNumber
        ? layoutSwitch.joints.find((joint) => joint.number == presentationJointNumber)
        : undefined;

    // Use presentation joint as main joint if possible, otherwise use first joint
    const switchFeature = new OlFeature<OlPoint>({
        geometry: gvtToOlPoint(
            presentationJoint ? presentationJoint.location : layoutSwitch.joints[0].location,
        ),
    });

    switchFeature.setStyle(
        selected || highlighted
            ? selectedStyle(layoutSwitch, largeSymbols, linked)
            : unselectedStyle(layoutSwitch, largeSymbols, labels, linked),
    );
    switchFeature.set('switch-data', {
        switch: layoutSwitch,
        planId: planId,
    });
    switchFeature.setId(id);

    const jointFeatures =
        selected || highlighted
            ? layoutSwitch.joints.map((joint, index) => {
                  const feature = new OlFeature<OlPoint>({
                      geometry: gvtToOlPoint(joint.location),
                  });
                  feature.setStyle(
                      jointStyle(
                          joint,
                          // Again, use presentation joint as main joint if found, otherwise use first one
                          presentationJoint ? joint.number === presentationJointNumber : index == 0,
                      ),
                  );
                  feature.set('switch-data', {
                      switch: layoutSwitch,
                      joint: joint,
                      planId: planId,
                  });
                  feature.setId(`${id}_${index}`);
                  return feature;
              })
            : [];

    return [switchFeature].concat(jointFeatures);
}

const gvtToOlPoint = (point: Point): OlPoint => new OlPoint([point.x, point.y]);

function unselectedStyle(
    layoutSwitch: LayoutSwitch,
    large: boolean,
    textLabel: boolean,
    linked: boolean,
) {
    return new Style({
        zIndex: 0,
        renderer: createUnselectedSwitchRenderer(layoutSwitch, large, textLabel, linked),
    });
}

function selectedStyle(layoutSwitch: LayoutSwitch, large: boolean, linked: boolean) {
    return new Style({
        zIndex: 2,
        renderer: createSelectedSwitchLabelRenderer(layoutSwitch, large, linked),
    });
}

function jointStyle(joint: LayoutSwitchJoint, mainJoint: boolean) {
    return new Style({
        zIndex: mainJoint ? 2 : 1,
        renderer: createJointRenderer(joint, mainJoint),
    });
}

let switchIdCompare = '';
let switchChangeTimeCompare: TimeStamp | undefined = undefined;

adapterInfoRegister.add('switches', {
    createAdapter: function (
        mapTiles: MapTile[],
        existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
        mapLayer: SwitchLayer,
        selection: Selection,
        publishType: PublishType,
        _linkingState: LinkingState,
        changeTimes: ChangeTimes,
        olView: OlView,
        onViewContentChanged?: (items: OptionalShownItems) => void,
    ): OlLayerAdapter {
        const getSwitchesFromApi = () => {
            return Promise.all(
                mapTiles.map((t) => getSwitchesByTile(changeTimes.layoutSwitch, t, publishType)),
            ).then((switchGroups) => [...new Set(switchGroups.flatMap((switches) => switches))]);
        };

        const vectorSource = existingOlLayer?.getSource() || new VectorSource();
        // Use an existing layer or create a new one. Old layer is "recycled" to
        // prevent features to disappear while moving the map.
        const layer: VectorLayer<VectorSource<OlPoint>> =
            existingOlLayer ||
            new VectorLayer({
                source: vectorSource,
            });

        layer.setVisible(mapLayer.visible);

        const searchFunction = (hitArea: OlPolygon, options: SearchItemsOptions) => {
            const switches = getMatchingSwitches(
                hitArea,
                vectorSource.getFeaturesInExtent(hitArea.getExtent()),
                {
                    strategy: options.limit == 1 ? 'nearest' : 'limit',
                    limit: options.limit,
                },
            ).map((d) => d.switch);
            return {
                switches: switches,
            };
        };

        const switchesChanged = (newIds: LayoutSwitchId[]) => {
            if (onViewContentChanged) {
                const newCompare = `${publishType}${JSON.stringify(newIds.sort())}`;
                const changeTimeCompare = changeTimes.layoutSwitch;
                if (
                    newCompare !== switchIdCompare ||
                    changeTimeCompare !== switchChangeTimeCompare
                ) {
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
            Promise.all([getSwitchesFromApi(), getSwitchStructures()]).then(
                ([switches, switchStructures]) => {
                    const largeSymbols: boolean = resolution <= Limits.SWITCH_LARGE_SYMBOLS;
                    const labels: boolean = resolution <= Limits.SWITCH_LABELS;
                    const isSelected = (switchItem: LayoutSwitch) => {
                        return selection.selectedItems.switches.some((s) => s.id === switchItem.id);
                    };

                    const isHighlighted = (switchItem: LayoutSwitch) => {
                        return selection.highlightedItems.switches.some(
                            (s) => s.id === switchItem.id,
                        );
                    };

                    vectorSource.clear();
                    vectorSource.addFeatures(
                        createFeatures(
                            switches,
                            isSelected,
                            isHighlighted,
                            () => false,
                            publishType,
                            largeSymbols,
                            labels,
                            undefined,
                            switchStructures,
                        ),
                    );
                    switchesChanged(switches.map((s) => s.id));
                },
            );
        } else {
            vectorSource.clear();
            switchesChanged([]);
        }

        return {
            layer: layer,
            searchItems: searchFunction,
            searchShownItems: searchFunction,
        };
    },
});

adapterInfoRegister.add('geometrySwitches', {
    createAdapter: function (
        _mapTiles: MapTile[],
        existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
        mapLayer: GeometrySwitchLayer,
        selection: Selection,
        publishType: PublishType,
        _linkingState: LinkingState,
        _changeTimes: ChangeTimes,
        olView: OlView,
    ): OlLayerAdapter {
        const vectorSource = existingOlLayer?.getSource() || new VectorSource();
        // Use an existing layer or create a new one. Old layer is "recycled" to
        // prevent features to disappear while moving the map.
        const layer: VectorLayer<VectorSource<OlPoint>> =
            existingOlLayer ||
            new VectorLayer({
                source: vectorSource,
            });

        layer.setVisible(mapLayer.visible);

        const resolution = olView.getResolution() || 0;
        vectorSource.clear();

        if (resolution <= Limits.SWITCH_SHOW) {
            const largeSymbols: boolean = resolution <= Limits.SWITCH_LARGE_SYMBOLS;
            const labels: boolean = resolution <= Limits.SWITCH_LABELS;
            const isSelected = (switchItem: LayoutSwitch) => {
                return selection.selectedItems.geometrySwitches.some(
                    (s) => s.geometryItem.id === switchItem.id,
                );
            };

            const isHighlighted = (switchItem: LayoutSwitch) => {
                return selection.highlightedItems.geometrySwitches.some(
                    (s) => s.geometryItem.id === switchItem.id,
                );
            };

            const planStatusPromises = selection.planLayouts.map((plan) =>
                plan.planDataType == 'STORED'
                    ? getPlanLinkStatus(plan.planId, publishType).then((status) => ({
                          plan: plan,
                          status: status,
                      }))
                    : {
                          plan: plan,
                          status: undefined,
                      },
            );

            Promise.all([getSwitchStructures(), ...planStatusPromises]).then(
                ([switchStructures, ...statusResults]) => {
                    const features = statusResults.flatMap((statusResult) => {
                        const plan = statusResult.plan;
                        const switchLinkedStatus = statusResult.status
                            ? new Map(
                                  statusResult.status.switches.map((switchItem) => [
                                      switchItem.id,
                                      switchItem.isLinked,
                                  ]),
                              )
                            : undefined;

                        const isSwitchLinked = (switchItem: LayoutSwitch) =>
                            (switchItem.sourceId && switchLinkedStatus?.get(switchItem.sourceId)) ||
                            false;

                        return createFeatures(
                            plan.switches,
                            isSelected,
                            isHighlighted,
                            isSwitchLinked,
                            'OFFICIAL',
                            largeSymbols,
                            labels,
                            plan.planId,
                            switchStructures,
                        );
                    });
                    vectorSource.addFeatures(features);
                },
            );
        }

        return {
            layer: layer,
            searchItems: (hitArea: OlPolygon, options: SearchItemsOptions) => {
                const switches = getMatchingSwitches(
                    hitArea,
                    vectorSource.getFeaturesInExtent(hitArea.getExtent()),
                    {
                        strategy: options.limit == 1 ? 'nearest' : 'limit',
                        limit: options.limit,
                    },
                ).map((d) => {
                    return {
                        geometryItem: d.switch,
                        planId: d.planId as GeometryPlanId,
                    };
                });
                return {
                    geometrySwitches: switches,
                };
            },
        };
    },
    mapTileSizePx: calculateTileSize(0), // Everything at once anyhow
});
