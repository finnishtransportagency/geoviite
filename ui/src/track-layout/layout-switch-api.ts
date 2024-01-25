import { BoundingBox, Point } from 'model/geometry';
import { DraftableChangeInfo, PublishType, TimeStamp } from 'common/common-model';
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
import { filterNotEmpty, indexIntoMap } from 'utils/array-utils';
import { ValidatedAsset } from 'publication/publication-model';

const switchCache = asyncCache<string, LayoutSwitch | undefined>();
const switchGroupsCache = asyncCache<string, LayoutSwitch[]>();

const cacheKey = (id: LayoutSwitchId, publishType: PublishType) => `${id}_${publishType}`;

export async function getSwitchesByBoundingBox(
    bbox: BoundingBox,
    publishType: PublishType,
    comparisonPoint?: Point,
    includeSwitchesWithNoJoints = false,
): Promise<LayoutSwitch[]> {
    const params = queryParams({
        bbox: bboxString(bbox),
        comparisonPoint: comparisonPoint && pointString(comparisonPoint),
        includeSwitchesWithNoJoints: includeSwitchesWithNoJoints,
    });
    return await getNonNull<LayoutSwitch[]>(`${layoutUri('switches', publishType)}${params}`);
}

export async function getSwitchesByTile(
    changeTime: TimeStamp,
    mapTile: MapTile,
    publishType: PublishType,
): Promise<LayoutSwitch[]> {
    const tileKey = `${mapTile.id}_${publishType}`;
    return switchGroupsCache.get(changeTime, tileKey, () =>
        getSwitchesByBoundingBox(mapTile.area, publishType),
    );
}
export async function getSwitchesByName(
    publishType: PublishType,
    name: string,
): Promise<LayoutSwitch[]> {
    const params = queryParams({
        exactName: name,
        includeDeleted: true,
    });
    return await getNonNull<LayoutSwitch[]>(`${layoutUri('switches', publishType)}${params}`);
}

export async function getSwitch(
    switchId: LayoutSwitchId,
    publishType: PublishType,
    changeTime: TimeStamp = getChangeTimes().layoutSwitch,
): Promise<LayoutSwitch | undefined> {
    return switchCache.get(changeTime, cacheKey(switchId, publishType), () =>
        getNullable<LayoutSwitch>(layoutUri('switches', publishType, switchId)),
    );
}

export async function getSwitches(
    switchIds: LayoutSwitchId[],
    publishType: PublishType,
    changeTime: TimeStamp = getChangeTimes().layoutSwitch,
): Promise<LayoutSwitch[]> {
    return switchCache
        .getMany(
            changeTime,
            switchIds,
            (id) => cacheKey(id, publishType),
            (fetchIds) =>
                getNonNull<LayoutSwitch[]>(
                    `${layoutUri('switches', publishType)}?ids=${fetchIds}`,
                ).then((switches) => {
                    const switchMap = indexIntoMap<LayoutSwitchId, LayoutSwitch>(switches);
                    return (id) => switchMap.get(id);
                }),
        )
        .then((switches) => switches.filter(filterNotEmpty));
}

export async function getSwitchJointConnections(
    publishType: PublishType,
    id: LayoutSwitchId,
): Promise<LayoutSwitchJointConnection[]> {
    return getNonNull<LayoutSwitchJointConnection[]>(
        `${layoutUri('switches', publishType, id)}/joint-connections`,
    );
}

export async function insertSwitch(
    newSwitch: TrackLayoutSwitchSaveRequest,
): Promise<Result<LayoutSwitchId, TrackLayoutSaveError>> {
    const apiResult = await postNonNullAdt<TrackLayoutSwitchSaveRequest, LayoutSwitchId>(
        layoutUri('switches', 'DRAFT'),
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
): Promise<Result<LayoutSwitchId, TrackLayoutSaveError>> {
    const apiResult = await putNonNullAdt<TrackLayoutSwitchSaveRequest, LayoutSwitchId>(
        layoutUri('switches', 'DRAFT', id),
        updatedSwitch,
    );

    await updateSwitchChangeTime();

    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function deleteDraftSwitch(
    switchId: LayoutSwitchId,
): Promise<LayoutSwitchId | undefined> {
    return await deleteNonNull<LayoutSwitchId>(layoutUri('switches', 'DRAFT', switchId)).then((r) =>
        updateSwitchChangeTime().then((_) => r),
    );
}

export async function getSwitchValidation(
    publishType: PublishType,
    id: LayoutSwitchId,
): Promise<ValidatedAsset> {
    return getNonNull<ValidatedAsset>(`${layoutUri('switches', publishType, id)}/validations`);
}

export const getSwitchChangeTimes = (
    id: LayoutSwitchId,
    publishType: PublishType,
): Promise<DraftableChangeInfo | undefined> => {
    return getNonNull<DraftableChangeInfo>(changeTimeUri('switches', id, publishType));
};
