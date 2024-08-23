import {
    AddressPoint,
    AlignmentPoint,
    AlignmentStartAndEnd,
    DuplicateStatus,
    LayoutLocationTrack,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackDescription,
    LocationTrackId,
    LocationTrackInfoboxExtras,
    OperatingPoint,
} from 'track-layout/track-layout-model';
import {
    draftLayoutContext,
    LayoutAssetChangeInfo,
    LayoutContext,
    Oid,
    TimeStamp,
    TrackMeter,
} from 'common/common-model';
import {
    deleteNonNull,
    getNonNull,
    getNullable,
    postNonNull,
    putNonNull,
    queryParams,
} from 'api/api-fetch';
import { changeInfoUri, layoutUri } from 'track-layout/track-layout-api';
import { asyncCache } from 'cache/cache';
import { BoundingBox } from 'model/geometry';
import { bboxString } from 'common/common-api';
import { LocationTrackSaveRequest } from 'linking/linking-model';
import { getChangeTimes, updateLocationTrackChangeTime } from 'common/change-time-api';
import { isNilOrBlank } from 'utils/string-utils';
import { filterNotEmpty, indexIntoMap } from 'utils/array-utils';
import { ValidatedLocationTrack } from 'publication/publication-model';
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

export type SplitDuplicateMatch = 'FULL' | 'PARTIAL' | 'NONE';

// TODO: GVT-2525 combine into SplitDuplicate
export type SplitDuplicateStatus = {
    match: SplitDuplicateMatch;
    duplicateOfId: LocationTrackId | undefined;
    startSwitchId: LayoutSwitchId | undefined;
    endSwitchId: LayoutSwitchId | undefined;
    startPoint: AlignmentPoint | undefined;
    endPoint: AlignmentPoint | undefined;
};

export type SplitDuplicate = {
    id: LocationTrackId;
    name: string;
    start: AddressPoint;
    end: AddressPoint;
    length: number;
    status: DuplicateStatus;
};

export type LocationTrackDuplicate = {
    id: LocationTrackId;
    trackNumberId: LayoutTrackNumberId;
    name: string;
    externalId: Oid;
    duplicateStatus: DuplicateStatus;
};

export type SplitInitializationParameters = {
    id: LocationTrackId;
    switches: SwitchOnLocationTrack[];
    duplicates: SplitDuplicate[];
    nearestOperatingPointToEnd: OperatingPoint | undefined;
    nearestOperatingPointToStart: OperatingPoint | undefined;
};

const cacheKey = (id: LocationTrackId, layoutContext: LayoutContext) =>
    `${id}_${layoutContext.publicationState}_${layoutContext.designId}`;

export async function getLocationTrack(
    id: LocationTrackId,
    layoutContext: LayoutContext,
    changeTime: TimeStamp = getChangeTimes().layoutLocationTrack,
): Promise<LayoutLocationTrack | undefined> {
    return locationTrackCache.get(changeTime, cacheKey(id, layoutContext), () =>
        getNullable<LayoutLocationTrack>(layoutUri('location-tracks', layoutContext, id)),
    );
}

export async function getLocationTrackInfoboxExtras(
    id: LocationTrackId,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): Promise<LocationTrackInfoboxExtras | undefined> {
    const changeTime = getMaxTimestamp(
        changeTimes.layoutLocationTrack,
        changeTimes.layoutSwitch,
        changeTimes.split,
    );
    return locationTrackInfoboxExtrasCache.get(changeTime, cacheKey(id, layoutContext), () =>
        getNullable<LocationTrackInfoboxExtras>(
            `${layoutUri('location-tracks', layoutContext, id)}/infobox-extras`,
        ),
    );
}

export async function getRelinkableSwitchesCount(
    id: LocationTrackId,
    layoutContext: LayoutContext,
): Promise<number | undefined> {
    return getNullable<number>(
        `${layoutUri('location-tracks', layoutContext, id)}/relinkable-switches-count`,
    );
}

export async function getLocationTracksByName(
    trackNumberId: LayoutTrackNumberId,
    locationTrackNames: string[],
    layoutContext: LayoutContext,
    includeDeleted: boolean,
): Promise<LayoutLocationTrack[]> {
    const params = queryParams({ locationTrackNames, includeDeleted });
    return getNonNull<LayoutLocationTrack[]>(
        `${layoutUri('track-numbers', layoutContext)}/${trackNumberId}/location-tracks${params}`,
    );
}

export async function getLocationTracksBySearchTerm(
    searchTerm: string | undefined,
    layoutContext: LayoutContext,
    limit: number,
): Promise<LayoutLocationTrack[]> {
    if (isNilOrBlank(searchTerm)) return [];

    const params = queryParams({
        searchTerm: searchTerm,
        limit: limit,
        lang: i18next.language,
    });
    return await getNonNull<LayoutLocationTrack[]>(
        `${layoutUri('location-tracks', layoutContext)}${params}`,
    );
}

export function getLocationTrackDescriptions(
    locationTrackIds: LocationTrackId[],
    layoutContext: LayoutContext,
): Promise<LocationTrackDescription[] | undefined> {
    const params = queryParams({ ids: locationTrackIds.join(','), lang: i18next.language });
    return getNullable<LocationTrackDescription[]>(
        `${layoutUri('location-tracks', layoutContext)}/description${params}`,
    );
}

export async function fetchStartAndEnd(
    locationTrackId: LocationTrackId,
    layoutContext: LayoutContext,
): Promise<AlignmentStartAndEnd | undefined> {
    return getNullable<AlignmentStartAndEnd>(
        `${layoutUri('location-tracks', layoutContext, locationTrackId)}/start-and-end`,
    );
}

export async function getManyStartsAndEnds(
    locationTrackIds: LocationTrackId[],
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<AlignmentStartAndEnd[]> {
    return locationTrackStartAndEndCache
        .getMany(
            changeTime,
            locationTrackIds,
            (id) => cacheKey(id, layoutContext),
            (ids) =>
                getNonNull<AlignmentStartAndEnd[]>(
                    `${layoutUri('location-tracks', layoutContext)}/start-and-end${queryParams({
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
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<AlignmentStartAndEnd | undefined> {
    return locationTrackStartAndEndCache.get(
        changeTime,
        cacheKey(locationTrackId, layoutContext),
        () => fetchStartAndEnd(locationTrackId, layoutContext),
    );
}

export async function getLocationTracksNear(
    layoutContext: LayoutContext,
    bbox: BoundingBox,
): Promise<LayoutLocationTrack[]> {
    const params = queryParams({ bbox: bboxString(bbox) });
    return getNonNull<LayoutLocationTrack[]>(
        `${layoutUri('location-tracks', layoutContext)}${params}`,
    );
}

export async function insertLocationTrack(
    layoutContext: LayoutContext,
    locationTrack: LocationTrackSaveRequest,
): Promise<LocationTrackId> {
    const result = await postNonNull<LocationTrackSaveRequest, LocationTrackId>(
        layoutUri('location-tracks', draftLayoutContext(layoutContext)),
        locationTrack,
    );

    await updateLocationTrackChangeTime();

    return result;
}

export async function updateLocationTrack(
    layoutContext: LayoutContext,
    id: LocationTrackId,
    locationTrack: LocationTrackSaveRequest,
): Promise<LocationTrackId> {
    const apiResult = await putNonNull<LocationTrackSaveRequest, LocationTrackId>(
        layoutUri('location-tracks', draftLayoutContext(layoutContext), id),
        locationTrack,
    );

    await updateLocationTrackChangeTime();

    return apiResult;
}

export const deleteLocationTrack = async (
    layoutContext: LayoutContext,
    id: LocationTrackId,
): Promise<LocationTrackId> => {
    const result = await deleteNonNull<LocationTrackId>(
        layoutUri('location-tracks', draftLayoutContext(layoutContext), id),
    );

    await updateLocationTrackChangeTime();

    return result;
};

export async function getLocationTracks(
    ids: LocationTrackId[],
    layoutContext: LayoutContext,
    changeTime: TimeStamp = getChangeTimes().layoutLocationTrack,
): Promise<LayoutLocationTrack[]> {
    return locationTrackCache
        .getMany(
            changeTime,
            ids,
            (id) => cacheKey(id, layoutContext),
            (fetchIds) =>
                getNonNull<LayoutLocationTrack[]>(
                    `${layoutUri('location-tracks', layoutContext)}?ids=${fetchIds}`,
                ).then((tracks) => {
                    const trackMap = indexIntoMap(tracks);
                    return (id) => trackMap.get(id);
                }),
        )
        .then((tracks) => tracks.filter(filterNotEmpty));
}

export async function getNonLinkedLocationTracks(
    layoutContext: LayoutContext,
): Promise<LayoutLocationTrack[]> {
    return getNonNull<LayoutLocationTrack[]>(
        `${layoutUri('location-tracks', draftLayoutContext(layoutContext))}/non-linked`,
    );
}

export const getLocationTrackChangeTimes = (
    id: LocationTrackId,
    layoutContext: LayoutContext,
): Promise<LayoutAssetChangeInfo | undefined> => {
    return getNullable<LayoutAssetChangeInfo>(changeInfoUri('location-tracks', id, layoutContext));
};

export const getLocationTrackSectionsByPlan = async (
    layoutContext: LayoutContext,
    id: LocationTrackId,
    bbox: BoundingBox | undefined = undefined,
) => {
    const params = queryParams({ bbox: bbox ? bboxString(bbox) : undefined });
    return getNullable<AlignmentPlanSection[]>(
        `${layoutUri('location-tracks', layoutContext, id)}/plan-geometry${params}`,
    );
};

export const getSplittingInitializationParameters = async (
    layoutContext: LayoutContext,
    id: LocationTrackId,
): Promise<SplitInitializationParameters> => {
    return getNonNull<SplitInitializationParameters>(
        `${layoutUri('location-tracks', layoutContext, id)}/splitting-initialization-parameters`,
    );
};

export async function getLocationTrackValidation(
    layoutContext: LayoutContext,
    id: LocationTrackId,
): Promise<ValidatedLocationTrack | undefined> {
    return getNullable<ValidatedLocationTrack>(
        `${layoutUri('location-tracks', layoutContext, id)}/validation`,
    );
}
