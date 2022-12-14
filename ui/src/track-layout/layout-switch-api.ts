import { BoundingBox, Point } from 'model/geometry';
import { PublishType, TimeStamp } from 'common/common-model';
import {
    LayoutSwitch,
    LayoutSwitchId,
    LayoutSwitchJointConnection,
} from 'track-layout/track-layout-model';
import {
    deleteIgnoreError,
    getThrowError,
    getWithDefault,
    postAdt,
    putAdt,
    queryParams,
} from 'api/api-fetch';
import { layoutUri } from 'track-layout/track-layout-api';
import { bboxString, pointString } from 'common/common-api';
import { getChangeTimes, updateSwitchChangeTime } from 'common/change-time-api';
import { asyncCache } from 'cache/cache';
import { MapTile } from 'map/map-model';
import { Result } from 'neverthrow';
import { TrackLayoutSaveError, TrackLayoutSwitchSaveRequest } from 'linking/linking-model';

const switchCache = asyncCache<string, LayoutSwitch>();
const switchGroupsCache = asyncCache<string, LayoutSwitch[]>();

export async function getSwitchesByBoundingBox(
    bbox: BoundingBox,
    publishType: PublishType,
    _searchTerm?: string,
    comparisonPoint?: Point,
    includeSwitchesWithNoJoints = false,
): Promise<LayoutSwitch[]> {
    const params = queryParams({
        bbox: bboxString(bbox),
        comparisonPoint: comparisonPoint && pointString(comparisonPoint),
        includeSwitchesWithNoJoints: includeSwitchesWithNoJoints,
    });
    return await getWithDefault<LayoutSwitch[]>(
        `${layoutUri('switches', publishType)}${params}`,
        [],
    );
}

export async function getSwitchesBySearchTerm(
    searchTerm: string,
    publishType: PublishType,
    limit: number,
): Promise<LayoutSwitch[]> {
    const params = queryParams({
        searchTerm: searchTerm,
        limit: limit,
    });
    return await getWithDefault<LayoutSwitch[]>(
        `${layoutUri('switches', publishType)}${params}`,
        [],
    );
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

export async function getSwitch(
    switchId: LayoutSwitchId,
    publishType: PublishType,
): Promise<LayoutSwitch> {
    const cacheKey = `${switchId}_${publishType}`;
    return switchCache.get(getChangeTimes().layoutSwitch, cacheKey, () =>
        getThrowError<LayoutSwitch>(layoutUri('switches', publishType, switchId)),
    );
}

// NOTE: There's no front-end caching for mass fetches yet, so it's preferable to use separate single fetches if you
// know that the data is most likely in cache. Caching will be implemented in GVT-1428
export async function getSwitches(
    switchIds: LayoutSwitchId[],
    publishType: PublishType,
): Promise<LayoutSwitch[]> {
    return switchIds.length > 0
        ? getThrowError<LayoutSwitch[]>(`${layoutUri('switches', publishType)}?ids=${switchIds}`)
        : Promise.resolve([]);
}

export async function getSwitchJointConnections(
    publishType: PublishType,
    id: LayoutSwitchId,
): Promise<LayoutSwitchJointConnection[]> {
    return getWithDefault<LayoutSwitchJointConnection[]>(
        `${layoutUri('switches', publishType, id)}/joint-connections`,
        [],
    );
}

export async function insertSwitch(
    newSwitch: TrackLayoutSwitchSaveRequest,
): Promise<Result<LayoutSwitchId, TrackLayoutSaveError>> {
    const apiResult = await postAdt<TrackLayoutSwitchSaveRequest, LayoutSwitchId>(
        layoutUri('switches', 'DRAFT'),
        newSwitch,
        true,
    );
    updateSwitchChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function updateSwitch(
    id: LayoutSwitchId,
    updatedSwitch: TrackLayoutSwitchSaveRequest,
): Promise<Result<LayoutSwitchId, TrackLayoutSaveError>> {
    const apiResult = await putAdt<TrackLayoutSwitchSaveRequest, LayoutSwitchId>(
        layoutUri('switches', 'DRAFT', id),
        updatedSwitch,
        true,
    );
    updateSwitchChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function deleteDraftSwitch(switchId: LayoutSwitchId): Promise<LayoutSwitchId | null> {
    return await deleteIgnoreError<LayoutSwitchId>(layoutUri('switches', 'DRAFT', switchId)).then(
        (r) => {
            updateSwitchChangeTime();
            return r;
        },
    );
}
