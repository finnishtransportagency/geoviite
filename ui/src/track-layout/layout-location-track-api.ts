import {
    AlignmentStartAndEnd,
    LayoutLocationTrack,
    LocationTrackDescription,
    LocationTrackId,
    LocationTrackInfoboxExtras,
} from 'track-layout/track-layout-model';
import { ChangeTimes, PublishType, TimeStamp, TrackMeter } from 'common/common-model';
import { deleteAdt, getNonNull, getNullable, postAdt, putAdt, queryParams } from 'api/api-fetch';
import { changeTimeUri, layoutUri } from 'track-layout/track-layout-api';
import { asyncCache } from 'cache/cache';
import { BoundingBox } from 'model/geometry';
import { bboxString } from 'common/common-api';
import { LocationTrackSaveError, LocationTrackSaveRequest } from 'linking/linking-model';
import { Result } from 'neverthrow';
import { getChangeTimes, updateLocationTrackChangeTime } from 'common/change-time-api';
import { isNilOrBlank } from 'utils/string-utils';
import { filterNotEmpty, indexIntoMap } from 'utils/array-utils';
import { ValidatedAsset } from 'publication/publication-model';
import { GeometryPlanId } from 'geometry/geometry-model';
import i18next from 'i18next';
import { getMaxTimestamp } from 'utils/date-utils';

const locationTrackCache = asyncCache<string, LayoutLocationTrack | undefined>();
const locationTrackInfoboxExtrasCache = asyncCache<
    string,
    LocationTrackInfoboxExtras | undefined
>();
const locationTrackStartAndEndCache = asyncCache<string, AlignmentStartAndEnd | undefined>();

type PlanSectionPoint = {
    address: TrackMeter;
    m: number;
};

export type AlignmentPlanSection = {
    planId: GeometryPlanId | undefined;
    planName: string | undefined;
    alignmentName: string | undefined;
    isLinked: boolean;
    start: PlanSectionPoint | undefined;
    end: PlanSectionPoint | undefined;
    id: string;
};

const cacheKey = (id: LocationTrackId, publishType: PublishType) => `${id}_${publishType}`;

export async function getLocationTrack(
    id: LocationTrackId,
    publishType: PublishType,
    changeTime: TimeStamp = getChangeTimes().layoutLocationTrack,
): Promise<LayoutLocationTrack | undefined> {
    return locationTrackCache.get(changeTime, cacheKey(id, publishType), () =>
        getNullable<LayoutLocationTrack>(layoutUri('location-tracks', publishType, id)),
    );
}

export async function getLocationTrackInfoboxExtras(
    id: LocationTrackId,
    publishType: PublishType,
    changeTime: TimeStamp = getMaxTimestamp(
        getChangeTimes().layoutLocationTrack,
        getChangeTimes().layoutSwitch,
    ),
): Promise<LocationTrackInfoboxExtras | undefined> {
    return locationTrackInfoboxExtrasCache.get(changeTime, cacheKey(id, publishType), () =>
        getNullable<LocationTrackInfoboxExtras>(
            `${layoutUri('location-tracks', publishType, id)}/infobox-extras`,
        ),
    );
}

export async function getLocationTracksBySearchTerm(
    searchTerm: string | undefined,
    publishType: PublishType,
    limit: number,
): Promise<LayoutLocationTrack[]> {
    if (isNilOrBlank(searchTerm)) return [];

    const params = queryParams({
        searchTerm: searchTerm,
        limit: limit,
        lang: i18next.language,
    });
    return await getNonNull<LayoutLocationTrack[]>(
        `${layoutUri('location-tracks', publishType)}${params}`,
    );
}

export function getLocationTrackDescriptions(
    locationTrackIds: LocationTrackId[],
    publishType: PublishType,
): Promise<LocationTrackDescription[] | undefined> {
    const params = queryParams({ ids: locationTrackIds.join(',') });
    return getNullable<LocationTrackDescription[]>(
        `${layoutUri('location-tracks', publishType)}/description/${params}`,
    );
}

export async function fetchStartAndEnd(
    locationTrackId: LocationTrackId,
    publishType: PublishType,
): Promise<AlignmentStartAndEnd | undefined> {
    return getNullable<AlignmentStartAndEnd>(
        `${layoutUri('location-tracks', publishType, locationTrackId)}/start-and-end`,
    );
}

export async function getLocationTrackStartAndEnd(
    locationTrackId: LocationTrackId,
    publishType: PublishType,
    changeTime: TimeStamp,
): Promise<AlignmentStartAndEnd | undefined> {
    return locationTrackStartAndEndCache.get(
        changeTime,
        cacheKey(locationTrackId, publishType),
        () => fetchStartAndEnd(locationTrackId, publishType),
    );
}

export async function getLocationTracksNear(
    publishType: PublishType,
    bbox: BoundingBox,
): Promise<LayoutLocationTrack[]> {
    const params = queryParams({ bbox: bboxString(bbox) });
    return getNonNull<LayoutLocationTrack[]>(
        `${layoutUri('location-tracks', publishType)}${params}`,
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

export async function getLocationTracks(
    ids: LocationTrackId[],
    publishType: PublishType,
    changeTime: TimeStamp = getChangeTimes().layoutLocationTrack,
): Promise<LayoutLocationTrack[]> {
    return locationTrackCache
        .getMany(
            changeTime,
            ids,
            (id) => cacheKey(id, publishType),
            (fetchIds) =>
                getNonNull<LayoutLocationTrack[]>(
                    `${layoutUri('location-tracks', publishType)}?ids=${fetchIds}`,
                ).then((tracks) => {
                    const trackMap = indexIntoMap(tracks);
                    return (id) => trackMap.get(id);
                }),
        )
        .then((tracks) => tracks.filter(filterNotEmpty));
}

export async function getNonLinkedLocationTracks(): Promise<LayoutLocationTrack[]> {
    return getNonNull<LayoutLocationTrack[]>(`${layoutUri('location-tracks', 'DRAFT')}/non-linked`);
}

export const getLocationTrackChangeTimes = (
    id: LocationTrackId,
): Promise<ChangeTimes | undefined> => {
    return getNullable<ChangeTimes>(changeTimeUri('location-tracks', id));
};

export const getLocationTrackSectionsByPlan = async (
    publishType: PublishType,
    id: LocationTrackId,
    bbox: BoundingBox | undefined = undefined,
) => {
    const params = queryParams({ bbox: bbox ? bboxString(bbox) : undefined });
    return getNullable<AlignmentPlanSection[]>(
        `${layoutUri('location-tracks', publishType, id)}/plan-geometry/${params}`,
    );
};

export async function getLocationTrackValidation(
    publishType: PublishType,
    id: LocationTrackId,
): Promise<ValidatedAsset> {
    return getNonNull<ValidatedAsset>(
        `${layoutUri('location-tracks', publishType, id)}/validation`,
    );
}
