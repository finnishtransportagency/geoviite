import {
    AddressPoint,
    AlignmentStartAndEnd,
    LayoutLocationTrack,
    AlignmentPoint,
    LayoutTrackNumberId,
    LocationTrackDescription,
    LocationTrackId,
    LocationTrackInfoboxExtras,
} from 'track-layout/track-layout-model';
import { DraftableChangeInfo, PublishType, TimeStamp, TrackMeter } from 'common/common-model';
import {
    deleteNonNullAdt,
    getNonNull,
    getNullable,
    postNonNullAdt,
    putNonNullAdt,
    queryParams,
} from 'api/api-fetch';
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
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import i18next from 'i18next';
import { getMaxTimestamp } from 'utils/date-utils';
import { SwitchOnLocationTrack } from 'tool-panel/location-track/split-store';
import { ChangeTimes } from 'common/common-slice';

const locationTrackCache = asyncCache<string, LayoutLocationTrack | undefined>();
const locationTrackInfoboxExtrasCache = asyncCache<
    string,
    LocationTrackInfoboxExtras | undefined
>();
const locationTrackStartAndEndCache = asyncCache<string, AlignmentStartAndEnd | undefined>();

type PlanSectionPoint = {
    address: TrackMeter;
    location: AlignmentPoint;
    m: number;
};

export type AlignmentPlanSection = {
    planId: GeometryPlanId | undefined;
    planName: string | undefined;
    alignmentId: GeometryAlignmentId | undefined;
    alignmentName: string | undefined;
    isLinked: boolean;
    start: PlanSectionPoint | undefined;
    end: PlanSectionPoint | undefined;
    id: string;
};

export type SplitDuplicate = {
    id: LocationTrackId;
    name: string;
    start: AddressPoint;
    end: AddressPoint;
};

export type SplitInitializationParameters = {
    id: LocationTrackId;
    switches: SwitchOnLocationTrack[];
    duplicates: SplitDuplicate[];
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
    changeTimes: ChangeTimes,
): Promise<LocationTrackInfoboxExtras | undefined> {
    const changeTime = getMaxTimestamp(
        changeTimes.layoutLocationTrack,
        changeTimes.layoutSwitch,
        changeTimes.split,
    );
    return locationTrackInfoboxExtrasCache.get(changeTime, cacheKey(id, publishType), () =>
        getNullable<LocationTrackInfoboxExtras>(
            `${layoutUri('location-tracks', publishType, id)}/infobox-extras`,
        ),
    );
}

export async function getRelinkableSwitchesCount(
    id: LocationTrackId,
    publishType: PublishType,
): Promise<number | undefined> {
    return getNullable<number>(
        `${layoutUri('location-tracks', publishType, id)}/relinkable-switches-count`,
    );
}

export async function getLocationTracksByName(
    trackNumberId: LayoutTrackNumberId,
    locationTrackNames: string[],
    publishType: PublishType,
): Promise<LayoutLocationTrack[]> {
    const params = queryParams({ locationTrackNames });
    return getNonNull<LayoutLocationTrack[]>(
        `${layoutUri('track-numbers', publishType)}/${trackNumberId}/location-tracks${params}`,
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
        `${layoutUri('location-tracks', publishType)}/description${params}`,
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

export async function getManyStartsAndEnds(
    locationTrackIds: LocationTrackId[],
    publishType: PublishType,
    changeTime: TimeStamp,
): Promise<AlignmentStartAndEnd[]> {
    return locationTrackStartAndEndCache
        .getMany(
            changeTime,
            locationTrackIds,
            (id) => cacheKey(id, publishType),
            (ids) =>
                getNonNull<AlignmentStartAndEnd[]>(
                    `${layoutUri('location-tracks', publishType)}/start-and-end${queryParams({
                        ids,
                    })}`,
                ).then((startsAndEnds) => {
                    const startAndEndMap = indexIntoMap(startsAndEnds);
                    return (id) => startAndEndMap.get(id);
                }),
        )
        .then((startsAndEnds) => startsAndEnds.filter(filterNotEmpty));
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
    const apiResult = await postNonNullAdt<LocationTrackSaveRequest, LocationTrackId>(
        layoutUri('location-tracks', 'DRAFT'),
        locationTrack,
    );

    await updateLocationTrackChangeTime();

    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function updateLocationTrack(
    id: LocationTrackId,
    locationTrack: LocationTrackSaveRequest,
): Promise<Result<LocationTrackId, LocationTrackSaveError>> {
    const apiResult = await putNonNullAdt<LocationTrackSaveRequest, LocationTrackId>(
        layoutUri('location-tracks', 'DRAFT', id),
        locationTrack,
    );

    await updateLocationTrackChangeTime();

    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export const deleteLocationTrack = async (
    id: LocationTrackId,
): Promise<Result<LocationTrackId, LocationTrackSaveError>> => {
    const apiResult = await deleteNonNullAdt<undefined, LocationTrackId>(
        layoutUri('location-tracks', 'DRAFT', id),
        undefined,
    );

    await updateLocationTrackChangeTime();

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
    publishType: PublishType,
): Promise<DraftableChangeInfo | undefined> => {
    return getNullable<DraftableChangeInfo>(changeTimeUri('location-tracks', id, publishType));
};

export const getLocationTrackSectionsByPlan = async (
    publishType: PublishType,
    id: LocationTrackId,
    bbox: BoundingBox | undefined = undefined,
) => {
    const params = queryParams({ bbox: bbox ? bboxString(bbox) : undefined });
    return getNullable<AlignmentPlanSection[]>(
        `${layoutUri('location-tracks', publishType, id)}/plan-geometry${params}`,
    );
};

export const getSplittingInitializationParameters = async (
    publishType: PublishType,
    id: LocationTrackId,
): Promise<SplitInitializationParameters> => {
    return getNonNull<SplitInitializationParameters>(
        `${layoutUri('location-tracks', publishType, id)}/splitting-initialization-parameters`,
    );
};

export async function getLocationTrackValidation(
    publishType: PublishType,
    id: LocationTrackId,
): Promise<ValidatedAsset | undefined> {
    return getNullable<ValidatedAsset>(
        `${layoutUri('location-tracks', publishType, id)}/validation`,
    );
}
