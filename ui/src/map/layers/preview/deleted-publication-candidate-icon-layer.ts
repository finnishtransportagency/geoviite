import { LineString, Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile, OptionalShownItems } from 'map/map-model';
import {
    createLayer,
    findMatchingEntities,
    GeoviiteMapLayer,
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
    OperationalPoint,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import { Point, Rectangle } from 'model/geometry';
import Feature from 'ol/Feature';
import {
    draftMainLayoutContext,
    LayoutContext,
    officialLayoutContext,
    SwitchStructure,
} from 'common/common-model';
import {
    KmPostPublicationCandidate,
    OperationalPointPublicationCandidate,
    PublicationCandidate,
    SwitchPublicationCandidate,
} from 'publication/publication-model';
import { filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import { DesignPublicationMode } from 'preview/preview-tool-bar';
import { getSwitchesByTile } from 'track-layout/layout-switch-api';
import { getSwitchStructures } from 'common/common-api';
import { getKmPostsByTile } from 'track-layout/layout-km-post-api';
import { createKmPostBadgeFeature } from 'map/layers/utils/km-post-layer-utils';
import { SWITCH_LARGE_SYMBOLS, SWITCH_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { Style } from 'ol/style';
import { getDeletedSwitchRenderer } from 'map/layers/utils/switch-layer-utils';
import {
    CandidateDataProperties,
    getSwitchLocation,
} from 'map/layers/utils/publication-candidate-highlight-utils';
import { renderOperationalPointFeature } from 'map/layers/operational-point/operational-points-layer-utils';
import { getOperationalPoints } from 'track-layout/layout-operational-point-api';

let shownSwitchesCompare = '';
let shownKmPostsCompare = '';
let shownOperationalPointsCompare = '';

const updateShownSwitches = (
    switchIds: LayoutSwitchId[],
    onViewContentChanged: (items: OptionalShownItems) => void,
): void => {
    const compare = switchIds.sort().join();

    if (compare !== shownSwitchesCompare) {
        shownSwitchesCompare = compare;
        onViewContentChanged({ switches: switchIds });
    }
};

const updateShownKmPosts = (
    kmPostIds: LayoutKmPostId[],
    onViewContentChanged: (items: OptionalShownItems) => void,
): void => {
    const compare = kmPostIds.sort().join();

    if (compare !== shownKmPostsCompare) {
        shownKmPostsCompare = compare;
        onViewContentChanged({ kmPosts: kmPostIds });
    }
};

const updateShownOperationalPoints = (
    operationalPointIds: OperationalPointId[],
    onViewContentChanged: (items: OptionalShownItems) => void,
): void => {
    const compare = operationalPointIds.sort().join();

    if (compare !== shownOperationalPointsCompare) {
        shownOperationalPointsCompare = compare;
        onViewContentChanged({ operationalPoints: operationalPointIds });
    }
};

const deletedSwitchFeature = (location: Point, isLarge: boolean): Feature<OlPoint> => {
    const deletedIconFeature = new Feature({
        geometry: new OlPoint(pointToCoords(location)),
    });
    deletedIconFeature.setStyle(
        new Style({
            renderer: getDeletedSwitchRenderer(isLarge),
        }),
    );
    return deletedIconFeature;
};

const createFeatures = (
    switches: LayoutSwitch[],
    kmPosts: LayoutKmPost[],
    switchStructures: SwitchStructure[],
    operationalPoints: OperationalPoint[],
    resolution: number,
) => {
    const swFeatures = switches
        .flatMap((s) => {
            const structure = switchStructures.find((str) => str.id === s.switchStructureId);
            return structure ? createDeletedSwitchIconFeature(s, structure, resolution) : undefined;
        })
        .filter(filterNotEmpty);
    const kpFeatures = kmPosts
        .flatMap((kmPost) => createKmPostBadgeFeature(kmPost, 'DELETED'))
        .filter(filterNotEmpty);
    const opFeatures = operationalPoints
        .flatMap((operationalPoint) =>
            renderOperationalPointFeature(operationalPoint, 'DELETED', operationalPoint.location),
        )
        .filter(filterNotEmpty);

    return [...swFeatures, ...kpFeatures, ...opFeatures];
};

const createDeletedSwitchIconFeature = (
    s: LayoutSwitch,
    structure: SwitchStructure,
    resolution: number,
): Feature<OlPoint> | undefined => {
    const switchLocation = getSwitchLocation(s, structure);
    return switchLocation
        ? deletedSwitchFeature(switchLocation, resolution <= SWITCH_LARGE_SYMBOLS)
        : undefined;
};

const onLoadingChange = (
    switches: LayoutSwitch[],
    kmPosts: LayoutKmPost[],
    operationalPoints: OperationalPoint[],
    loading: boolean,
    onViewContentChanged: (items: OptionalShownItems) => void,
    onLoadingData: (loading: boolean) => void,
) => {
    if (!loading) {
        updateShownKmPosts(
            kmPosts.map((kmPost) => kmPost.id),
            onViewContentChanged,
        );
        updateShownSwitches(
            switches.map((sw) => sw.id),
            onViewContentChanged,
        );
        updateShownOperationalPoints(
            operationalPoints.map((op) => op.id),
            onViewContentChanged,
        );
    }
    onLoadingData(loading);
};

const layerName: MapLayerName = 'deleted-publication-candidate-icon-layer';

const getKmPostsTiledPromise = (
    mapTiles: MapTile[],
    layerLayoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    deletedKmPostCandidates: KmPostPublicationCandidate[],
): Promise<LayoutKmPost[]> =>
    Promise.all(
        mapTiles.map((t) =>
            getKmPostsByTile(layerLayoutContext, changeTimes.layoutKmPost, t.area, 0),
        ),
    ).then((kmPosts) => {
        return kmPosts
            .flat()
            .filter(filterUniqueById((s) => s.id))
            .filter((kp) => deletedKmPostCandidates.some((candidate) => candidate.id === kp.id));
    });

const getSwitchesTiledPromise = (
    mapTiles: MapTile[],
    changeTimes: ChangeTimes,
    layerLayoutContext: LayoutContext,
    deletedSwitchCandidates: SwitchPublicationCandidate[],
): Promise<LayoutSwitch[]> =>
    Promise.all(
        mapTiles.map((t) => getSwitchesByTile(changeTimes.layoutSwitch, t, layerLayoutContext)),
    ).then((switches) => {
        return switches
            .flat()
            .filter(filterUniqueById((s) => s.id))
            .filter((s) => deletedSwitchCandidates.some((candidate) => candidate.id === s.id));
    });

const getOperationalPointsTiledPromise = (
    mapTiles: MapTile[],
    layerLayoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    deletedOperationalPointCandidates: OperationalPointPublicationCandidate[],
): Promise<OperationalPoint[]> =>
    Promise.all(
        mapTiles.map((t) =>
            getOperationalPoints(t, layerLayoutContext, changeTimes.operationalPoints),
        ),
    ).then((operationalPoints) => {
        return operationalPoints
            .flat()
            .filter(filterUniqueById((s) => s.id))
            .filter((op) =>
                deletedOperationalPointCandidates.some((candidate) => candidate.id === op.id),
            );
    });

export function createDeletedPublicationCandidateIconLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString | OlPoint | Rectangle> | undefined,
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

    const deletedKmPostCandidates = publicationCandidates
        .filter((c) => c.type === 'KM_POST')
        .filter((c) => c.operation === 'DELETE');
    const deletedSwitchCandidates = publicationCandidates
        .filter((c) => c.type === 'SWITCH')
        .filter((c) => c.operation === 'DELETE');
    const deletedOperationalPointCandidates = publicationCandidates
        .filter((c) => c.type === 'OPERATIONAL_POINT')
        .filter((c) => c.operation === 'DELETE');

    const deletedSwitchesPromise =
        resolution <= SWITCH_SHOW && deletedSwitchCandidates.length > 0
            ? getSwitchesTiledPromise(
                  mapTiles,
                  changeTimes,
                  layerLayoutContext,
                  deletedSwitchCandidates,
              )
            : Promise.resolve([]);
    const kmPostPromise =
        resolution < SWITCH_SHOW && deletedKmPostCandidates.length > 0
            ? getKmPostsTiledPromise(
                  mapTiles,
                  layerLayoutContext,
                  changeTimes,
                  deletedKmPostCandidates,
              )
            : Promise.resolve([]);
    const operationalPointsPromise =
        deletedOperationalPointCandidates.length > 0
            ? getOperationalPointsTiledPromise(
                  mapTiles,
                  layerLayoutContext,
                  changeTimes,
                  deletedOperationalPointCandidates,
              )
            : Promise.resolve([]);

    const allPromises = Promise.all([
        deletedSwitchesPromise,
        kmPostPromise,
        getSwitchStructures(),
        operationalPointsPromise,
    ]).then(([switches, kmPosts, switchStructures, operationalPoints]) => ({
        switches,
        kmPosts,
        switchStructures,
        operationalPoints,
    }));

    loadLayerData(
        source,
        isLatest,
        (loading, data) =>
            onLoadingChange(
                data?.switches ?? [],
                data?.kmPosts ?? [],
                data?.operationalPoints ?? [],
                loading,
                onViewContentChanged,
                onLoadingData,
            ),
        allPromises,
        (data) =>
            createFeatures(
                data.switches,
                data.kmPosts,
                data.switchStructures,
                data.operationalPoints,
                resolution,
            ),
    );

    return {
        name: layerName,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => ({
            switchPublicationCandidates: findMatchingEntities<SwitchPublicationCandidate>(
                hitArea,
                source,
                CandidateDataProperties.SWITCH,
                options,
            ),
            kmPostPublicationCandidates: findMatchingEntities<KmPostPublicationCandidate>(
                hitArea,
                source,
                CandidateDataProperties.KM_POST,
                options,
            ),
            operationalPointPublicationCandidates: findMatchingEntities(
                hitArea,
                source,
                CandidateDataProperties.OPERATIONAL_POINT,
                options,
            ),
        }),
        onRemove: () => {
            updateShownKmPosts([], onViewContentChanged);
            updateShownSwitches([], onViewContentChanged);
            updateShownOperationalPoints([], onViewContentChanged);
        },
    };
}
