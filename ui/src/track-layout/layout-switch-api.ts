import { BoundingBox, Point } from 'model/geometry';
import {
    DraftableChangeInfo,
    draftLayoutContext,
    LayoutContext,
    TimeStamp,
} from 'common/common-model';
import {
    LayoutSwitch,
    LayoutSwitchId,
    LayoutSwitchJointConnection,
} from 'track-layout/track-layout-model';
import {
    deleteNonNull,
    getNonNull,
    getNullable,
    postNonNullAdt,
    putNonNullAdt,
    queryParams,
} from 'api/api-fetch';
import { changeTimeUri, layoutUri } from 'track-layout/track-layout-api';
import { bboxString, pointString } from 'common/common-api';
import { getChangeTimes, updateSwitchChangeTime } from 'common/change-time-api';
import { asyncCache } from 'cache/cache';
import { MapTile } from 'map/map-model';
import { Result } from 'neverthrow';
import { TrackLayoutSaveError, TrackLayoutSwitchSaveRequest } from 'linking/linking-model';
import { filterNotEmpty, first, indexIntoMap } from 'utils/array-utils';
import { ValidatedAsset } from 'publication/publication-model';
import { getMaxTimestamp } from 'utils/date-utils';

const switchCache = asyncCache<string, LayoutSwitch | undefined>();
const switchGroupsCache = asyncCache<string, LayoutSwitch[]>();
const switchValidationCache = asyncCache<string, ValidatedAsset>();
const tiledSwitchValidationCache = asyncCache<string, ValidatedAsset[]>();

const cacheKey = (id: LayoutSwitchId, layoutContext: LayoutContext) =>
    `${id}_${layoutContext.publicationState}_${layoutContext.designId}`;

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

export async function getSwitchesByTile(
    changeTime: TimeStamp,
    mapTile: MapTile,
    layoutContext: LayoutContext,
): Promise<LayoutSwitch[]> {
    const tileKey = `${mapTile.id}_${layoutContext.publicationState}_${layoutContext.designId}`;
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
    newSwitch: TrackLayoutSwitchSaveRequest,
    layoutContext: LayoutContext,
): Promise<Result<LayoutSwitchId, TrackLayoutSaveError>> {
    const apiResult = await postNonNullAdt<TrackLayoutSwitchSaveRequest, LayoutSwitchId>(
        layoutUri('switches', draftLayoutContext(layoutContext)),
        newSwitch,
    );

    await updateSwitchChangeTime();

    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function updateSwitch(
    id: LayoutSwitchId,
    updatedSwitch: TrackLayoutSwitchSaveRequest,
    layoutContext: LayoutContext,
): Promise<Result<LayoutSwitchId, TrackLayoutSaveError>> {
    const apiResult = await putNonNullAdt<TrackLayoutSwitchSaveRequest, LayoutSwitchId>(
        layoutUri('switches', draftLayoutContext(layoutContext), id),
        updatedSwitch,
    );

    await updateSwitchChangeTime();

    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function deleteDraftSwitch(
    layoutContext: LayoutContext,
    switchId: LayoutSwitchId,
): Promise<LayoutSwitchId | undefined> {
    return await deleteNonNull<LayoutSwitchId>(
        layoutUri('switches', draftLayoutContext(layoutContext), switchId),
    ).then((r) => updateSwitchChangeTime().then((_) => r));
}

export const getSwitchValidation = async (
    id: LayoutSwitchId,
    layoutContext: LayoutContext,
): Promise<ValidatedAsset | undefined> =>
    getSwitchesValidation(layoutContext, [id]).then((switches) => first(switches));

export const getSwitchesValidation = async (
    layoutContext: LayoutContext,
    ids: LayoutSwitchId[],
) => {
    const changeTimes = getChangeTimes();
    const maxTime = getMaxTimestamp(changeTimes.layoutLocationTrack, changeTimes.layoutSwitch);
    const fetchOperation = (fetchIds: LayoutSwitchId[]) =>
        getNonNull<ValidatedAsset[]>(
            `${layoutUri('switches', layoutContext)}/validation?ids=${fetchIds}`,
        ).then((switches) => {
            const switchValidationMap = indexIntoMap<LayoutSwitchId, ValidatedAsset>(switches);
            return (id: LayoutSwitchId) => switchValidationMap.get(id) as ValidatedAsset;
        });
    return switchValidationCache
        .getMany(maxTime, ids, (id) => id, fetchOperation)
        .then((switches) => switches.filter(filterNotEmpty));
};

export const getSwitchesValidationByTile = async (
    changeTime: TimeStamp,
    mapTile: MapTile,
    layoutContext: LayoutContext,
): Promise<ValidatedAsset[]> => {
    const tileKey = `${mapTile.id}_${layoutContext.publicationState}`;
    return tiledSwitchValidationCache.get(changeTime, tileKey, () =>
        getNonNull<ValidatedAsset[]>(
            `${layoutUri('switches', layoutContext)}/validation${queryParams({
                bbox: bboxString(mapTile.area),
            })}`,
        ),
    );
};

export const getSwitchChangeTimes = (
    id: LayoutSwitchId,
    layoutContext: LayoutContext,
): Promise<DraftableChangeInfo | undefined> => {
    return getNonNull<DraftableChangeInfo>(changeTimeUri('switches', id, layoutContext));
};
