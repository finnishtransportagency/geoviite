import { LineString, Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile, OptionalShownItems } from 'map/map-model';
import { createLayer, findMatchingEntities, loadLayerData } from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { ChangeTimes } from 'common/common-slice';
import {
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';
import {
    LayoutKmPost,
    LayoutKmPostId,
    LayoutSwitch,
    LayoutSwitchId,
} from 'track-layout/track-layout-model';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import Feature from 'ol/Feature';
import {
    draftMainLayoutContext,
    LayoutContext,
    officialLayoutContext,
    SwitchStructure,
} from 'common/common-model';
import {
    DraftChangeType,
    KmPostPublicationCandidate,
    PublicationCandidate,
    SwitchPublicationCandidate,
} from 'publication/publication-model';
import {
    createPointCandidateFeature,
    KM_POST_CANDIDATE_DATA_PROPERTY,
    SWITCH_CANDIDATE_DATA_PROPERTY,
} from 'map/layers/highlight/publication-candidate-layer';
import { filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import { DesignPublicationMode } from 'preview/preview-tool-bar';
import { getSwitchesByTile } from 'track-layout/layout-switch-api';
import { createSwitchFeature } from 'map/layers/utils/switch-layer-utils';
import { getSwitchStructures } from 'common/common-api';
import { getKmPostsByTile } from 'track-layout/layout-km-post-api';
import { createKmPostBadgeFeature } from 'map/layers/utils/km-post-layer-utils';

let shownSwitchesCompare = '';
let shownKmPostsCompare = '';

const layerName: MapLayerName = 'preview-deleted-point-features-layer';

export function createDeletedPreviewPointFeaturesLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<LineString | OlPoint | Rectangle>> | undefined,
    publicationCandidates: PublicationCandidate[],
    designPublicationMode: DesignPublicationMode,
    isSplitting: boolean,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    onViewContentChanged: (items: OptionalShownItems) => void,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);
    const layerLayoutContext =
        designPublicationMode === 'PUBLISH_CHANGES'
            ? officialLayoutContext(layoutContext)
            : draftMainLayoutContext();

    layer.setOpacity(
        isSplitting ? OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING : NORMAL_ALIGNMENT_OPACITY,
    );

    function updateShownSwitches(switchIds: LayoutSwitchId[]) {
        const compare = switchIds.sort().join();

        if (compare !== shownSwitchesCompare) {
            shownSwitchesCompare = compare;
            onViewContentChanged({ switches: switchIds });
        }
    }

    function updateShownKmPosts(kmPostIds: LayoutKmPostId[]) {
        const compare = kmPostIds.sort().join();

        if (compare !== shownKmPostsCompare) {
            shownKmPostsCompare = compare;
            onViewContentChanged({ kmPosts: kmPostIds });
        }
    }

    const switchesPromise =
        publicationCandidates.length > 0
            ? Promise.all(
                  mapTiles.map((t) =>
                      getSwitchesByTile(changeTimes.layoutSwitch, t, layerLayoutContext),
                  ),
              ).then((switches) => {
                  return switches
                      .flat()
                      .filter(filterUniqueById((s) => s.id))
                      .filter((s) =>
                          publicationCandidates.some(
                              (candidate) =>
                                  candidate.type === DraftChangeType.SWITCH &&
                                  candidate.id === s.id,
                          ),
                      )
                      .map((s) => ({
                          switch: s,
                          candidate: publicationCandidates.find(
                              (c) => c.type === DraftChangeType.SWITCH && c.id === s.id,
                          ) as SwitchPublicationCandidate | undefined,
                      }));
              })
            : Promise.resolve([]);
    const kmPostPromise =
        publicationCandidates.length > 0
            ? Promise.all(
                  mapTiles.map((t) =>
                      getKmPostsByTile(layerLayoutContext, changeTimes.layoutKmPost, t.area, 0),
                  ),
              ).then((kmPosts) => {
                  return kmPosts
                      .flat()
                      .filter(filterUniqueById((s) => s.id))
                      .filter((kp) =>
                          publicationCandidates.some(
                              (candidate) =>
                                  candidate.type === DraftChangeType.KM_POST &&
                                  candidate.id === kp.id,
                          ),
                      )
                      .map((s) => ({
                          kmPost: s,
                          candidate: publicationCandidates.find(
                              (c) => c.type === DraftChangeType.KM_POST && c.id === s.id,
                          ) as KmPostPublicationCandidate | undefined,
                      }));
              })
            : Promise.resolve([]);
    const allPromises = Promise.all([switchesPromise, kmPostPromise, getSwitchStructures()]).then(
        ([switches, kmPosts, switchStructures]) => ({
            switches,
            kmPosts,
            switchStructures,
        }),
    );

    const createFeatures = (alignments: {
        switches: {
            switch: LayoutSwitch;
            candidate: SwitchPublicationCandidate | undefined;
        }[];
        kmPosts: {
            kmPost: LayoutKmPost;
            candidate: KmPostPublicationCandidate | undefined;
        }[];
        switchStructures: SwitchStructure[];
    }) => {
        const swFeatures = alignments.switches
            .flatMap(({ switch: s, candidate }) => {
                const structure = alignments.switchStructures.find(
                    (structure) => structure.id === s.switchStructureId,
                );
                const presentationJointNumber = structure?.presentationJointNumber;
                const presentationJointLocation = s.joints.find(
                    (j) => j.number === presentationJointNumber,
                )?.location;
                const feature = candidate
                    ? createSwitchFeature(
                          s,
                          false,
                          false,
                          false,
                          true,
                          false,
                          true,
                          undefined,
                          presentationJointNumber,
                          undefined,
                      )
                    : undefined;
                const highlightFeature =
                    candidate && presentationJointLocation
                        ? createPointCandidateFeature(
                              candidate,
                              presentationJointLocation,
                              DraftChangeType.SWITCH,
                              true,
                              -1,
                          )
                        : undefined;
                highlightFeature?.set(SWITCH_CANDIDATE_DATA_PROPERTY, candidate);

                return [feature, [highlightFeature]];
            })
            .flat()
            .filter(filterNotEmpty);
        const kpFeatures = alignments.kmPosts
            .flatMap(({ kmPost, candidate }) => {
                const feature = createKmPostBadgeFeature(kmPost);
                const highlightFeature =
                    candidate && kmPost.layoutLocation
                        ? createPointCandidateFeature(
                              candidate,
                              kmPost.layoutLocation,
                              DraftChangeType.SWITCH,
                              true,
                              -1,
                          )
                        : undefined;
                highlightFeature?.set(KM_POST_CANDIDATE_DATA_PROPERTY, candidate);

                return [feature, [highlightFeature]];
            })
            .flat()
            .filter(filterNotEmpty);

        return [...swFeatures, ...kpFeatures];
    };

    const onLoadingChange = (
        loading: boolean,
        data:
            | {
                  switches: {
                      switch: LayoutSwitch;
                      candidate: SwitchPublicationCandidate | undefined;
                  }[];
                  kmPosts: {
                      kmPost: LayoutKmPost;
                      candidate: KmPostPublicationCandidate | undefined;
                  }[];
                  switchStructures: SwitchStructure[];
              }
            | undefined,
    ) => {
        if (!loading) {
            updateShownKmPosts(data?.kmPosts?.map(({ kmPost }) => kmPost.id) ?? []);
            updateShownSwitches(data?.switches?.map(({ switch: sw }) => sw.id) ?? []);
        }
        onLoadingData(loading);
    };

    loadLayerData(source, isLatest, onLoadingChange, allPromises, createFeatures);

    return {
        name: layerName,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => ({
            switchPublicationCandidates: findMatchingEntities<SwitchPublicationCandidate>(
                hitArea,
                source,
                SWITCH_CANDIDATE_DATA_PROPERTY,
                options,
            ),
            kmPostPublicationCandidates: findMatchingEntities<KmPostPublicationCandidate>(
                hitArea,
                source,
                KM_POST_CANDIDATE_DATA_PROPERTY,
                options,
            ),
        }),
        onRemove: () => {
            updateShownKmPosts([]);
            updateShownSwitches([]);
        },
    };
}
