import { BoundingBox, Point } from 'model/geometry';
import {
    DesignBranch,
    draftLayoutContext,
    LayoutAssetChangeInfo,
    LayoutBranch,
    LayoutContext,
    Oid,
    TimeStamp,
} from 'common/common-model';
import {
    LayoutStateCategory,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutSwitchJointConnection,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import { deleteNonNull, getNonNull, getNullable, postNonNull, putNonNull, queryParams, } from 'api/api-fetch';
import {
    changeInfoUri,
    layoutUri,
    layoutUriByBranch,
    layoutUriWithoutContext,
    TRACK_LAYOUT_URI,
} from 'track-layout/track-layout-api';
import { bboxString, pointString } from 'common/common-api';
import { getChangeTimes, updateLocationTrackChangeTime, updateSwitchChangeTime, } from 'common/change-time-api';
import { asyncCache } from 'cache/cache';
import { MapTile } from 'map/map-model';
import { LayoutSwitchSaveRequest } from 'linking/linking-model';
import { filterNotEmpty, first, indexIntoMap } from 'utils/array-utils';
import { ValidatedSwitch } from 'publication/publication-model';
import { getMaxTimestamp } from 'utils/date-utils';

const switchCache = asyncCache<string, LayoutSwitch | undefined>();
const switchGroupsCache = asyncCache<string, LayoutSwitch[]>();
const switchValidationCache = asyncCache<string, ValidatedSwitch>();
const tiledSwitchValidationCache = asyncCache<string, ValidatedSwitch[]>();
const switchOidsCache = asyncCache<LayoutSwitchId, { [key in LayoutBranch]?: Oid } | undefined>();
const switchesWithinOperationalPointCache = asyncCache<
    string,
    SwitchWithOperationalPointPolygonInclusions[]
>();

const cacheKey = (id: LayoutSwitchId, layoutContext: LayoutContext) =>
    `${id}_${layoutContext.publicationState}_${layoutContext.branch}`;

export async function getSwitchesByBoundingBox(
    bbox: BoundingBox,
    layoutContext: LayoutContext,
    comparisonPoint?: Point,
    includeSwitchesWithNoJoints = false,
): Promise<LayoutSwitch[]> {
    const params = queryParams({
        bbox: bboxString(bbox),
        comparisonPoint: comparisonPoint && pointString(comparisonPoint),
        includeSwitchesWithNoJoints: includeSwitchesWithNoJoints,
    });
    return await getNonNull<LayoutSwitch[]>(`${layoutUri('switches', layoutContext)}${params}`);
}

export type SwitchAreaSummary = {
    switchCount: number;
    switches: LayoutSwitch[];
};

export async function getSwitchesAreaSummary(
    bbox: BoundingBox,
    layoutContext: LayoutContext,
    maxSwitches: number,
    includeSwitchesWithNoJoints = false,
): Promise<SwitchAreaSummary> {
    const params = queryParams({
        bbox: bboxString(bbox),
        maxSwitches: maxSwitches.toString(),
        includeSwitchesWithNoJoints: includeSwitchesWithNoJoints,
    });
    return await getNonNull<SwitchAreaSummary>(
        `${layoutUri('switches', layoutContext)}/area-summary${params}`,
    );
}

export async function getSwitchesByTile(
    changeTime: TimeStamp,
    mapTile: MapTile,
    layoutContext: LayoutContext,
): Promise<LayoutSwitch[]> {
    const tileKey = `${mapTile.id}_${layoutContext.publicationState}_${layoutContext.branch}`;
    return switchGroupsCache.get(changeTime, tileKey, () =>
        getSwitchesByBoundingBox(mapTile.area, layoutContext),
    );
}

export async function getSwitchesByName(
    layoutContext: LayoutContext,
    name: string,
): Promise<LayoutSwitch[]> {
    const params = queryParams({
        exactName: name,
        includeDeleted: true,
    });
    return await getNonNull<LayoutSwitch[]>(`${layoutUri('switches', layoutContext)}${params}`);
}

export async function getSwitch(
    switchId: LayoutSwitchId,
    layoutContext: LayoutContext,
    changeTime: TimeStamp = getChangeTimes().layoutSwitch,
): Promise<LayoutSwitch | undefined> {
    return switchCache.get(changeTime, cacheKey(switchId, layoutContext), () =>
        getNullable<LayoutSwitch>(layoutUri('switches', layoutContext, switchId)),
    );
}

export async function getSwitches(
    switchIds: LayoutSwitchId[],
    layoutContext: LayoutContext,
    changeTime: TimeStamp = getChangeTimes().layoutSwitch,
): Promise<LayoutSwitch[]> {
    return switchCache
        .getMany(
            changeTime,
            switchIds,
            (id) => cacheKey(id, layoutContext),
            (fetchIds) =>
                getNonNull<LayoutSwitch[]>(
                    `${layoutUri('switches', layoutContext)}?ids=${fetchIds}`,
                ).then((switches) => {
                    const switchMap = indexIntoMap<LayoutSwitchId, LayoutSwitch>(switches);
                    return (id) => switchMap.get(id);
                }),
        )
        .then((switches) => switches.filter(filterNotEmpty));
}

export async function getSwitchJointConnections(
    layoutContext: LayoutContext,
    id: LayoutSwitchId,
): Promise<LayoutSwitchJointConnection[]> {
    return getNonNull<LayoutSwitchJointConnection[]>(
        `${layoutUri('switches', layoutContext, id)}/joint-connections`,
    );
}

export async function insertSwitch(
    newSwitch: LayoutSwitchSaveRequest,
    layoutContext: LayoutContext,
): Promise<LayoutSwitchId> {
    const result = await postNonNull<LayoutSwitchSaveRequest, LayoutSwitchId>(
        layoutUri('switches', draftLayoutContext(layoutContext)),
        newSwitch,
    );
    await updateSwitchChangeTime();
    return result;
}

export async function updateSwitch(
    id: LayoutSwitchId,
    updatedSwitch: LayoutSwitchSaveRequest,
    layoutContext: LayoutContext,
    deleteSwitchLinks?: boolean,
): Promise<LayoutSwitchId> {
    const queryParams =
        deleteSwitchLinks !== undefined ? `?deleteSwitchLinks=${deleteSwitchLinks}` : '';
    const result = await putNonNull<LayoutSwitchSaveRequest, LayoutSwitchId>(
        `${layoutUri('switches', draftLayoutContext(layoutContext), id)}${queryParams}`,
        updatedSwitch,
    );
    // Switch changes can also affect location track names & descriptions
    await Promise.all([updateSwitchChangeTime(), updateLocationTrackChangeTime()]);
    return result;
}

export async function deleteDraftSwitch(
    layoutContext: LayoutContext,
    switchId: LayoutSwitchId,
): Promise<LayoutSwitchId | undefined> {
    const result = await deleteNonNull<LayoutSwitchId>(
        layoutUri('switches', draftLayoutContext(layoutContext), switchId),
    );
    // Switch changes can also affect location track names & descriptions
    await Promise.all([updateSwitchChangeTime(), updateLocationTrackChangeTime()]);
    return result;
}

export const getSwitchValidation = async (
    layoutContext: LayoutContext,
    id: LayoutSwitchId,
): Promise<ValidatedSwitch | undefined> =>
    getSwitchesValidation(layoutContext, [id]).then((switches) => first(switches));

export const getSwitchesValidation = async (
    layoutContext: LayoutContext,
    ids: LayoutSwitchId[],
) => {
    const changeTimes = getChangeTimes();
    const maxTime = getMaxTimestamp(changeTimes.layoutLocationTrack, changeTimes.layoutSwitch);
    const fetchOperation = (fetchIds: LayoutSwitchId[]) =>
        getNonNull<ValidatedSwitch[]>(
            `${layoutUri('switches', layoutContext)}/validation?ids=${fetchIds}`,
        ).then((switches) => {
            const switchValidationMap = indexIntoMap<LayoutSwitchId, ValidatedSwitch>(switches);
            return (id: LayoutSwitchId) => switchValidationMap.get(id) as ValidatedSwitch;
        });
    const cacheKey = (id: LayoutSwitchId) =>
        `${layoutContext.branch}_${layoutContext.publicationState}_${id}`;
    return switchValidationCache
        .getMany(maxTime, ids, cacheKey, fetchOperation)
        .then((switches) => switches.filter(filterNotEmpty));
};

export const getSwitchesValidationByTile = async (
    changeTime: TimeStamp,
    mapTile: MapTile,
    layoutContext: LayoutContext,
): Promise<ValidatedSwitch[]> => {
    const tileKey = `${mapTile.id}_${layoutContext.publicationState}_${layoutContext.branch}`;
    return tiledSwitchValidationCache.get(changeTime, tileKey, () =>
        getNonNull<ValidatedSwitch[]>(
            `${layoutUri('switches', layoutContext)}/validation${queryParams({
                bbox: bboxString(mapTile.area),
            })}`,
        ),
    );
};

export const getSwitchChangeTimes = (
    id: LayoutSwitchId,
    layoutContext: LayoutContext,
): Promise<LayoutAssetChangeInfo | undefined> => {
    return getNonNull<LayoutAssetChangeInfo>(changeInfoUri('switches', id, layoutContext));
};

export async function cancelSwitch(design: DesignBranch, id: LayoutSwitchId): Promise<void> {
    const result: Promise<void> = postNonNull(
        `${layoutUriByBranch('switches', design)}/${id}/cancel`,
        '',
    );
    // Switch changes can also affect location track names & descriptions
    await Promise.all([updateSwitchChangeTime(), updateLocationTrackChangeTime()]);
    return result;
}

export async function getSwitchOids(
    id: LayoutSwitchId,
    changeTime: TimeStamp,
): Promise<{ [key in LayoutBranch]?: Oid }> {
    const oids = await switchOidsCache.get(changeTime, id, () =>
        getNullable<{ [key in LayoutBranch]?: Oid }>(
            `${layoutUriWithoutContext('switches', id)}/oids`,
        ),
    );
    return oids ?? {};
}

export async function getSwitchOidPresence(oid: Oid): Promise<SwitchOidPresence> {
    return getNonNull<SwitchOidPresence>(`${TRACK_LAYOUT_URI}/switches/oid_presence/${oid}`);
}

export async function linkSwitchesToOperationalPoint(
    branch: LayoutBranch,
    switchIds: LayoutSwitchId[],
    operationalPointId: OperationalPointId,
): Promise<LayoutSwitchId[]> {
    return postNonNull<LayoutSwitchId[], LayoutSwitchId[]>(
        `${layoutUriByBranch('switches', branch)}/draft/link-to-operational-point/${operationalPointId}`,
        switchIds,
    );
}

export async function unlinkSwitchesFromOperationalPoint(
    branch: LayoutBranch,
    switchIds: LayoutSwitchId[],
): Promise<LayoutSwitchId[]> {
    return postNonNull<LayoutSwitchId[], LayoutSwitchId[]>(
        `${layoutUriByBranch('switches', branch)}/draft/unlink-from-operational-point/`,
        switchIds,
    );
}

export async function findOperationalPointSwitches(
    context: LayoutContext,
    operationalPointId: OperationalPointId,
): Promise<SwitchWithOperationalPointPolygonInclusions[]> {
    const changeTimes = getChangeTimes();
    const changeTime = getMaxTimestamp(changeTimes.layoutSwitch, changeTimes.operationalPoints);
    const key = `${context.publicationState}_${context.branch}_${operationalPointId}`;
    return switchesWithinOperationalPointCache.get(changeTime, key, () =>
        getNonNull<SwitchWithOperationalPointPolygonInclusions[]>(
            `${layoutUri('switches', context)}/by-operational-point/${operationalPointId}`,
        ),
    );
}

export type SwitchWithOperationalPointPolygonInclusions = {
    switchId: LayoutSwitchId;
    withinPolygon: OperationalPointId[];
};

export type SwitchOidPresence = {
    existsInRatko?: boolean;
    existsInGeoviiteAs?: GeoviiteSwitchOidPresence;
};
export type GeoviiteSwitchOidPresence = {
    id: LayoutSwitchId;
    stateCategory: LayoutStateCategory;
    name: string;
};

export type SwitchNameFixPreview = {
    switchId: LayoutSwitchId;
    currentName: string;
    fixedName: string;
};

export async function previewSwitchNameFixes(
    bbox: BoundingBox,
    layoutContext: LayoutContext,
): Promise<SwitchNameFixPreview[]> {
    const params = queryParams({ bbox: bboxString(bbox) });
    return await getNonNull<SwitchNameFixPreview[]>(
        `${layoutUri('switches', layoutContext)}/preview-name-fixes${params}`,
    );
}

export async function fixSwitchNames(
    branch: LayoutBranch,
    switchIds: LayoutSwitchId[],
): Promise<LayoutSwitchId[]> {
    const result = await postNonNull<LayoutSwitchId[], LayoutSwitchId[]>(
        `${layoutUriByBranch('switches', branch)}/draft/fix-names`,
        switchIds,
    );
    await updateSwitchChangeTime();
    return result;
}
