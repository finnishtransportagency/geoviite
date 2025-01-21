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
import { getSwitchStructures } from 'common/common-api';
import { getKmPostsByTile } from 'track-layout/layout-km-post-api';
import { createKmPostBadgeFeature } from 'map/layers/utils/km-post-layer-utils';
import { SWITCH_LARGE_SYMBOLS, SWITCH_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { Style } from 'ol/style';
import { getDeletedSwitchRenderer } from 'map/layers/utils/switch-layer-utils';

let shownSwitchesCompare = '';
let shownKmPostsCompare = '';

function deletedSwitchFeature(location: Point, isLarge: boolean): Feature<OlPoint> {
    const deletedIconFeature = new Feature({
        geometry: new OlPoint(pointToCoords(location)),
    });
    deletedIconFeature.setStyle(
        new Style({
            renderer: getDeletedSwitchRenderer(isLarge),
        }),
    );
    return deletedIconFeature;
}

function createDeletedSwitchIconFeature(
    s: LayoutSwitch,
    structure: SwitchStructure,
    resolution: number,
): Feature<OlPoint> | undefined {
    const presentationJointNumber = structure?.presentationJointNumber;
    const presentationJoint = s.joints.find((joint) => joint.number === presentationJointNumber);

    return presentationJoint
        ? deletedSwitchFeature(presentationJoint.location, resolution <= SWITCH_LARGE_SYMBOLS)
        : undefined;
}

const layerName: MapLayerName = 'preview-deleted-point-icon-features-layer';
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

    const deletedKmPostCandidates = publicationCandidates.filter(
        (c) => c.type === 'KM_POST' && c.operation === 'DELETE',
    );
    const deletedSwitchCandidates = publicationCandidates.filter(
        (c) => c.type === 'SWITCH' && c.operation === 'DELETE',
    );

    const deletedSwitchesPromise =
        resolution <= SWITCH_SHOW && deletedSwitchCandidates.length > 0
            ? Promise.all(
                  mapTiles.map((t) =>
                      getSwitchesByTile(changeTimes.layoutSwitch, t, layerLayoutContext),
                  ),
              ).then((switches) => {
                  return switches
                      .flat()
                      .filter(filterUniqueById((s) => s.id))
                      .filter((s) =>
                          deletedSwitchCandidates.some((candidate) => candidate.id === s.id),
                      );
              })
            : Promise.resolve([]);
    const kmPostPromise =
        resolution < SWITCH_SHOW && deletedKmPostCandidates.length > 0
            ? Promise.all(
                  mapTiles.map((t) =>
                      getKmPostsByTile(layerLayoutContext, changeTimes.layoutKmPost, t.area, 0),
                  ),
              ).then((kmPosts) => {
                  return kmPosts
                      .flat()
                      .filter(filterUniqueById((s) => s.id))
                      .filter((kp) =>
                          deletedKmPostCandidates.some((candidate) => candidate.id === kp.id),
                      );
              })
            : Promise.resolve([]);

    const allPromises = Promise.all([
        deletedSwitchesPromise,
        kmPostPromise,
        getSwitchStructures(),
    ]).then(([switches, kmPosts, switchStructures]) => ({
        switches,
        kmPosts,
        switchStructures,
    }));

    const createFeatures = (alignments: {
        switches: LayoutSwitch[];
        kmPosts: LayoutKmPost[];
        switchStructures: SwitchStructure[];
    }) => {
        const swFeatures = alignments.switches
            .flatMap((s) => {
                const structure = alignments.switchStructures.find(
                    (str) => str.id === s.switchStructureId,
                );
                return structure
                    ? createDeletedSwitchIconFeature(s, structure, resolution)
                    : undefined;
            })
            .filter(filterNotEmpty);
        const kpFeatures = alignments.kmPosts
            .flatMap((kmPost) => createKmPostBadgeFeature(kmPost, 'DELETED'))
            .filter(filterNotEmpty);

        return [...swFeatures, ...kpFeatures];
    };

    const onLoadingChange = (
        loading: boolean,
        data:
            | {
                  switches: LayoutSwitch[];
                  kmPosts: LayoutKmPost[];
                  switchStructures: SwitchStructure[];
              }
            | undefined,
    ) => {
        if (!loading) {
            updateShownKmPosts(data?.kmPosts?.map((kmPost) => kmPost.id) ?? []);
            updateShownSwitches(data?.switches?.map((sw) => sw.id) ?? []);
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
