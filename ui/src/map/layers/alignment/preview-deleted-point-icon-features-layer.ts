import { LineString, Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile, OptionalShownItems } from 'map/map-model';
import {
    createLayer,
    findMatchingEntities,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { ChangeTimes } from 'common/common-slice';
import {
    LayoutKmPost,
    LayoutKmPostId,
    LayoutSwitch,
    LayoutSwitchId,
} from 'track-layout/track-layout-model';
import { Point, Rectangle } from 'model/geometry';
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
import { SWITCH_LARGE_SYMBOLS, SWITCH_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { Style } from 'ol/style';
import { drawCircle, getCanvasRenderer } from 'map/layers/utils/rendering';
import { expectCoordinate } from 'utils/type-utils';
import styles from 'map/map.module.scss';
import { State } from 'ol/render';

let shownSwitchesCompare = '';
let shownKmPostsCompare = '';

const layerName: MapLayerName = 'preview-deleted-point-icon-features-layer';

function deletedIconBadgeFeature(location: Point, isLarge: boolean): Feature<OlPoint> {
    const deletedIconFeature = new Feature({
        geometry: new OlPoint(pointToCoords(location)),
    });
    deletedIconFeature.setStyle(
        new Style({
            zIndex: 10,
            renderer: getCanvasRenderer(
                undefined,
                (ctx: CanvasRenderingContext2D, { pixelRatio }: State) => {
                    ctx.lineWidth = pixelRatio;
                },
                [
                    (_, coord, ctx, { pixelRatio }) => {
                        ctx.fillStyle = styles.unlinkedSwitchJoint;
                        ctx.strokeStyle = styles.switchMainJointTextColor;
                        ctx.lineWidth = pixelRatio;
                        const offset = 0;

                        const [x, y] = expectCoordinate(coord);
                        const offsetX = x + offset * pixelRatio;
                        const offsetY = y - offset * pixelRatio;
                        drawCircle(ctx, offsetX, offsetY, (isLarge ? 6.5 : 4.5) * pixelRatio);
                        ctx.moveTo(offsetX - 2 * pixelRatio, offsetY - 2 * pixelRatio);
                        ctx.lineTo(offsetX + 2 * pixelRatio, offsetY + 2 * pixelRatio);

                        ctx.moveTo(offsetX - 2 * pixelRatio, offsetY + 2 * pixelRatio);
                        ctx.lineTo(offsetX + 2 * pixelRatio, offsetY - 2 * pixelRatio);
                        ctx.stroke();
                    },
                ],
            ),
        }),
    );
    return deletedIconFeature;
}

export function createDeletedPreviewPointIconFeaturesLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<LineString | OlPoint | Rectangle>> | undefined,
    publicationCandidates: PublicationCandidate[],
    designPublicationMode: DesignPublicationMode,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    onViewContentChanged: (items: OptionalShownItems) => void,
    onLoadingData: (loading: boolean) => void,
    resolution: number,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const layerLayoutContext =
        designPublicationMode === 'PUBLISH_CHANGES'
            ? officialLayoutContext(layoutContext)
            : draftMainLayoutContext();

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
        resolution <= SWITCH_SHOW && publicationCandidates.length > 0
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
                                  candidate.operation === 'DELETE' &&
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
        resolution < SWITCH_SHOW && publicationCandidates.length > 0
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
                                  candidate.operation === 'DELETE' &&
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
                const presentationJoint = s.joints.find(
                    (joint) => joint.number == presentationJointNumber,
                );

                const feature = candidate
                    ? createSwitchFeature(
                          s,
                          false,
                          false,
                          false,
                          resolution <= SWITCH_LARGE_SYMBOLS,
                          false,
                          false,
                          undefined,
                          presentationJointNumber,
                          undefined,
                      )
                    : undefined;
                const deletedIconFeature = presentationJoint
                    ? deletedIconBadgeFeature(
                          presentationJoint.location,
                          resolution <= SWITCH_LARGE_SYMBOLS,
                      )
                    : undefined;

                return [feature, deletedIconFeature];
            })
            .flat()
            .filter(filterNotEmpty);
        const kpFeatures = alignments.kmPosts
            .flatMap(({ kmPost }) => {
                const feature = createKmPostBadgeFeature(kmPost, true);
                const _deletedIconFeature = kmPost.layoutLocation
                    ? deletedIconBadgeFeature(kmPost.layoutLocation, true)
                    : undefined;

                return [feature];
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
