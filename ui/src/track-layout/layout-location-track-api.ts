import {
    AlignmentStartAndEnd,
    LayoutLocationTrack,
    LayoutLocationTrackDuplicate,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { ChangeTimes, PublishType, TimeStamp, TrackMeter } from 'common/common-model';
import {
    deleteAdt,
    getIgnoreError,
    getThrowError,
    getWithDefault,
    postAdt,
    putAdt,
    queryParams,
} from 'api/api-fetch';
import { changeTimeUri, layoutUri } from 'track-layout/track-layout-api';
import { asyncCache } from 'cache/cache';
import { BoundingBox } from 'model/geometry';
import { bboxString } from 'common/common-api';
import {
    LocationTrackEndpoint,
    LocationTrackSaveError,
    LocationTrackSaveRequest,
} from 'linking/linking-model';
import { Result } from 'neverthrow';
import {
    getChangeTimes,
    updateAllChangeTimes,
    updateLocationTrackChangeTime,
} from 'common/change-time-api';
import { MapTile } from 'map/map-model';
import { isNullOrBlank } from 'utils/string-utils';
import { GeometryPlanId } from 'geometry/geometry-model';

const locationTrackCache = asyncCache<string, LayoutLocationTrack | null>();
const locationTrackEndpointsCache = asyncCache<string, LocationTrackEndpoint[]>();

export type AlignmentSectionByPlan = {
    planName: string | undefined;
    planId: GeometryPlanId | undefined;
    startAddress: TrackMeter;
    endAddress: TrackMeter;
};

export async function getLocationTrack(
    id: LocationTrackId,
    publishType: PublishType,
    changeTime?: TimeStamp,
): Promise<LayoutLocationTrack | null> {
    const cacheKey = `${id}_${publishType}`;
    return locationTrackCache.get(
        changeTime || getChangeTimes().layoutLocationTrack,
        cacheKey,
        () => getIgnoreError<LayoutLocationTrack>(layoutUri('location-tracks', publishType, id)),
    );
}

export async function getLocationTracksBySearchTerm(
    searchTerm: string,
    publishType: PublishType,
    limit: number,
): Promise<LayoutLocationTrack[]> {
    if (isNullOrBlank(searchTerm)) return [];

    const params = queryParams({
        searchTerm: searchTerm,
        limit: limit,
    });
    return await getWithDefault<LayoutLocationTrack[]>(
        `${layoutUri('location-tracks', publishType)}${params}`,
        [],
    );
}

export async function getLocationTrackStartAndEnd(
    locationTrackId: LocationTrackId,
    publishType: PublishType,
): Promise<AlignmentStartAndEnd | undefined> {
    return getThrowError<AlignmentStartAndEnd>(
        `${layoutUri('location-tracks', publishType, locationTrackId)}/start-and-end`,
    );
}

export async function getLocationTracksNear(
    publishType: PublishType,
    bbox: BoundingBox,
): Promise<LayoutLocationTrack[]> {
    const params = queryParams({ bbox: bboxString(bbox) });
    return getThrowError<LayoutLocationTrack[]>(
        `${layoutUri('location-tracks', publishType)}${params}`,
    );
}

export async function getLocationTrackDuplicates(
    publishType: PublishType,
    id: LocationTrackId,
): Promise<LayoutLocationTrackDuplicate[]> {
    return getThrowError<LayoutLocationTrackDuplicate[]>(
        `${layoutUri('location-tracks', publishType, id)}/duplicate-of`,
    );
}

export async function insertLocationTrack(
    locationTrack: LocationTrackSaveRequest,
): Promise<Result<LocationTrackId, LocationTrackSaveError>> {
    const apiResult = await postAdt<LocationTrackSaveRequest, LocationTrackId>(
        layoutUri('location-tracks', 'DRAFT'),
        locationTrack,
        true,
    );
    updateLocationTrackChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function updateLocationTrack(
    id: LocationTrackId,
    locationTrack: LocationTrackSaveRequest,
): Promise<Result<LocationTrackId, LocationTrackSaveError>> {
    const apiResult = await putAdt<LocationTrackSaveRequest, LocationTrackId>(
        layoutUri('location-tracks', 'DRAFT', id),
        locationTrack,
        true,
    );
    updateLocationTrackChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export const deleteLocationTrack = async (
    id: LocationTrackId,
): Promise<Result<LocationTrackId, LocationTrackSaveError>> => {
    const apiResult = await deleteAdt<undefined, LocationTrackId>(
        layoutUri('location-tracks', 'DRAFT', id),
        undefined,
        true,
    );
    updateLocationTrackChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
};

// There's no front-end caching for mass fetches yet, so it's preferable to use separate single fetches if you
// know that the data is most likely in cache. Caching will be implemented in GVT-1428
export async function getLocationTracks(ids: LocationTrackId[], publishType: PublishType) {
    return ids.length > 0
        ? getThrowError<LayoutLocationTrack[]>(
              `${layoutUri('location-tracks', publishType)}?ids=${ids}`,
          )
        : Promise.resolve([]);
}

export async function getNonLinkedLocationTracks(): Promise<LayoutLocationTrack[]> {
    return getWithDefault<LayoutLocationTrack[]>(
        `${layoutUri('location-tracks', 'DRAFT')}/non-linked`,
        [],
    );
}

export const getLocationTrackChangeTimes = (id: LocationTrackId): Promise<ChangeTimes | null> => {
    return getIgnoreError<ChangeTimes>(changeTimeUri('location-tracks', id));
};

export async function getLocationTrackEndpointsByTile(
    mapTile: MapTile,
    publishType: PublishType,
): Promise<LocationTrackEndpoint[]> {
    const key = mapTile.id + publishType;
    const params = queryParams({
        bbox: bboxString(mapTile.area),
    });
    // TODO: This is an odd hack. You should instead bind the changetime in through the using view
    const changeTimes = await updateAllChangeTimes();
    const uri = `${layoutUri('location-tracks', publishType)}/end-points${params}`;
    return locationTrackEndpointsCache
        .get(changeTimes.layoutLocationTrack, key, () => getWithDefault(uri, []))
        .then((locationTrackEndpoints) =>
            locationTrackEndpoints.map((locationTrackEndpoint) => ({
                ...locationTrackEndpoint,
                // Generate id
                id:
                    locationTrackEndpoint.locationTrackId +
                    JSON.stringify(locationTrackEndpoint.location),
            })),
        );
}

export const getLocationTrackSectionsByPlan = async (
    publishType: PublishType,
    id: LocationTrackId,
    bbox: BoundingBox | undefined = undefined,
) => {
    const params = queryParams({ bbox: bbox ? bboxString(bbox) : undefined });
    return getIgnoreError<AlignmentSectionByPlan[]>(
        `${layoutUri('location-tracks', publishType, id)}/plan-geometry/${params}`,
    );
};
