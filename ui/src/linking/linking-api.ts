import { Result } from 'neverthrow';
import {
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutSwitchId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import {
    API_URI,
    deleteAdt,
    deleteIgnoreError,
    getIgnoreError,
    getThrowError,
    postAdt,
    postIgnoreError,
    putAdt,
    putIgnoreError,
    queryParams,
} from 'api/api-fetch';
import {
    GeometryPlanLinkStatus,
    IntervalRequest,
    KmPostSaveError,
    KmPostSaveRequest,
    LinkingGeometryWithAlignmentParameters,
    LinkingGeometryWithEmptyAlignmentParameters,
    LocationTrackEndpoint,
    LocationTrackEndPointConnectedUpdateRequest,
    LocationTrackSaveError,
    LocationTrackSaveRequest,
    LocationTrackTypeUpdateRequest,
    SuggestedSwitch,
    SuggestedSwitchCreateParams,
    SwitchLinkingParameters,
    TrackLayoutSaveError,
    TrackLayoutSwitchSaveRequest,
} from 'linking/linking-model';
import {
    getChangeTimes,
    updateAllChangeTimes,
    updateKmPostChangeTime,
    updateLocationTrackChangeTime,
    updatePlanChangeTime,
    updateReferenceLineChangeTime,
    updateSwitchChangeTime,
} from 'common/change-time-api';
import { PublishType, RowVersion, SwitchStructureId } from 'common/common-model';
import { asyncCache } from 'cache/cache';
import { GeometryAlignmentId, GeometryKmPostId, GeometryPlanId } from 'geometry/geometry-model';
import { MapTile } from 'map/map-model';
import { getMaxTimestamp } from 'utils/date-utils';
import { getSuggestedSwitchId } from 'linking/linking-utils';
import { bboxString, pointString } from 'common/common-api';
import { BoundingBox, Point } from 'model/geometry';
import { filterNotEmpty } from 'utils/array-utils';

const LINKING_URI = `${API_URI}/linking`;

const geometryElementsLinkedStatusCache = asyncCache<GeometryPlanId, GeometryPlanLinkStatus>();
const suggestedSwitchesCache = asyncCache<string, SuggestedSwitch[]>();
const locationTrackEndpointsCache = asyncCache<string, LocationTrackEndpoint[]>();

export const getSuggestedContinuousLocationTracks = async (
    locationTrackId: LocationTrackId,
    locationTrackEndPoint: LocationTrackEndpoint,
    bbox: BoundingBox,
): Promise<LayoutLocationTrack[]> => {
    const params = queryParams({
        id: locationTrackId,
        location: pointString(locationTrackEndPoint.location),
        locationTrackPointUpdateType: locationTrackEndPoint.updateType,
        bbox: bboxString(bbox),
    });
    return getThrowError<LayoutLocationTrack[]>(`${LINKING_URI}/suggested-alignments${params}`);
};

export const linkGeometryWithReferenceLine = async (
    parameters: LinkingGeometryWithAlignmentParameters,
): Promise<RowVersion | null> => {
    const url = `${LINKING_URI}/reference-lines/geometry`;
    const response = await postIgnoreError<LinkingGeometryWithAlignmentParameters, RowVersion>(
        url,
        parameters,
    );
    updateReferenceLineChangeTime();
    return response;
};

export const linkGeometryWithLocationTrack = async (
    parameters: LinkingGeometryWithAlignmentParameters,
): Promise<RowVersion | null> => {
    const url = `${LINKING_URI}/location-tracks/geometry`;
    const response = await postIgnoreError<LinkingGeometryWithAlignmentParameters, RowVersion>(
        url,
        parameters,
    );
    updateLocationTrackChangeTime();
    return response;
};

export const linkGeometryWithEmptyReferenceLine = async (
    parameters: LinkingGeometryWithEmptyAlignmentParameters,
): Promise<RowVersion | null> => {
    const url = `${LINKING_URI}/reference-lines/empty-geometry`;
    const response = await postIgnoreError<LinkingGeometryWithEmptyAlignmentParameters, RowVersion>(
        url,
        parameters,
    );
    updateReferenceLineChangeTime();
    return response;
};

export const linkGeometryWithEmptyLocationTrack = async (
    parameters: LinkingGeometryWithEmptyAlignmentParameters,
): Promise<RowVersion | null> => {
    const url = `${LINKING_URI}/location-tracks/empty-geometry`;
    const response = await postIgnoreError<LinkingGeometryWithEmptyAlignmentParameters, RowVersion>(
        url,
        parameters,
    );
    updateLocationTrackChangeTime();
    return response;
};

export async function insertLocationTrack(
    locationTrack: LocationTrackSaveRequest,
): Promise<Result<LocationTrackId, LocationTrackSaveError>> {
    const apiResult = await postAdt<LocationTrackSaveRequest, LocationTrackId>(
        `${LINKING_URI}/location-tracks`,
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
        `${LINKING_URI}/location-tracks/${id}`,
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
        `${LINKING_URI}/location-tracks/${id}`,
        undefined,
        true,
    );
    updateLocationTrackChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
};

export async function updateSwitch(
    id: LayoutSwitchId,
    updatedSwitch: TrackLayoutSwitchSaveRequest,
): Promise<Result<LayoutSwitchId, TrackLayoutSaveError>> {
    const apiResult = await putAdt<TrackLayoutSwitchSaveRequest, LayoutSwitchId>(
        `${LINKING_URI}/switches/${id}`,
        updatedSwitch,
        true,
    );
    updateSwitchChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function updateReferenceLineGeometry(
    id: ReferenceLineId,
    interval: IntervalRequest,
): Promise<string | null> {
    const result = await putIgnoreError<IntervalRequest, ReferenceLineId>(
        `${LINKING_URI}/reference-lines/${id}/geometry`,
        interval,
    );
    updateReferenceLineChangeTime();
    return result;
}

export async function updateLocationTrackGeometry(
    id: LocationTrackId,
    interval: IntervalRequest,
): Promise<string | null> {
    const result = await putIgnoreError<IntervalRequest, LocationTrackId>(
        `${LINKING_URI}/location-tracks/${id}/geometry`,
        interval,
    );
    updateLocationTrackChangeTime();
    return result;
}

export async function getLinkedAlignmentIdsInPlan(
    planId: GeometryPlanId,
    publishType: PublishType,
): Promise<GeometryAlignmentId[]> {
    return getPlanLinkStatus(planId, publishType).then((planStatus) =>
        planStatus.alignments.filter((a) => a.isLinked).map((a) => a.id),
    );
}

export async function getPlanLinkStatus(
    planId: GeometryPlanId,
    publishType: PublishType,
): Promise<GeometryPlanLinkStatus> {
    const changeTimes = getChangeTimes();
    const maxChangeTime = getMaxTimestamp(
        changeTimes.layoutReferenceLine,
        changeTimes.layoutLocationTrack,
        changeTimes.layoutSwitch,
        changeTimes.layoutKmPost,
    );

    return geometryElementsLinkedStatusCache.get(maxChangeTime, `${publishType}_${planId}`, () =>
        getThrowError(`${LINKING_URI}/${publishType}/plans/${planId}/status`),
    );
}

export async function insertSwitch(
    newSwitch: TrackLayoutSwitchSaveRequest,
): Promise<Result<LayoutSwitchId, TrackLayoutSaveError>> {
    const apiResult = await postAdt<TrackLayoutSwitchSaveRequest, LayoutSwitchId>(
        `${LINKING_URI}/switches`,
        newSwitch,
        true,
    );
    updateSwitchChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function deleteDraftSwitch(switchId: LayoutSwitchId): Promise<LayoutSwitchId | null> {
    return await deleteIgnoreError<LayoutSwitchId>(`${LINKING_URI}/switches/${switchId}`).then(
        (r) => {
            updateSwitchChangeTime();
            return r;
        },
    );
}

export async function getSuggestedSwitchesByTile(mapTile: MapTile): Promise<SuggestedSwitch[]> {
    const key = mapTile.id;
    const params = queryParams({
        bbox: bboxString(mapTile.area),
    });
    return (
        suggestedSwitchesCache
            .get(
                getMaxTimestamp(
                    getChangeTimes().layoutLocationTrack,
                    getChangeTimes().layoutSwitch,
                ),
                key,
                () => getThrowError(`${LINKING_URI}/suggested-switch${params}`),
            )
            // IDs are needed to separate different suggested switches from each other.
            // If suggested switch is generated from geometry switch, geom switch id
            // can be used as a id, otherwise create id from other attributes and
            // JSON serialize that object.
            .then((suggestedSwitches) =>
                suggestedSwitches.map((suggestedSwitch) => {
                    return {
                        ...suggestedSwitch,
                        id: getSuggestedSwitchId(suggestedSwitch),
                    };
                }),
            )
    );
}

export async function getSuggestedSwitchByPoint(point: Point, switchStructureId: SwitchStructureId): Promise<SuggestedSwitch[]> {
    const params = queryParams({
        location: pointString(point),
        switchStructureId: switchStructureId,
    });
    return getIgnoreError<SuggestedSwitch[]>(`${LINKING_URI}/suggested-switch${params}`)
        .then((suggestedSwitches) => {
                return (suggestedSwitches || [])
                    .filter(filterNotEmpty)
                    .map((suggestedSwitch) => {
                        return {
                            ...suggestedSwitch,
                            id: getSuggestedSwitchId(suggestedSwitch),
                        };
                    });
            },
        );
}


export async function linkSwitch(params: SwitchLinkingParameters): Promise<string> {
    const result = await postIgnoreError<SwitchLinkingParameters, string>(
        `${LINKING_URI}/switch-linking`,
        params,
    );
    if (!result) {
        throw Error('Failed to link switch!');
    }
    updateLocationTrackChangeTime();
    updateSwitchChangeTime();
    return result;
}

export async function getLocationTrackEndpointsByTile(
    mapTile: MapTile,
    publishType: PublishType,
): Promise<LocationTrackEndpoint[]> {
    const key = mapTile.id + publishType;
    const params = queryParams({
        bbox: bboxString(mapTile.area),
    });
    const changeTimes = await updateAllChangeTimes();
    return locationTrackEndpointsCache
        .get(changeTimes.layoutLocationTrack, key, () =>
            getThrowError(`${LINKING_URI}/${publishType}/location-tracks/end-points${params}`),
        )
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

export async function createSuggestedSwitch(
    params: SuggestedSwitchCreateParams,
): Promise<SuggestedSwitch | null> {
    return postIgnoreError<SuggestedSwitchCreateParams, SuggestedSwitch[]>(
        `${LINKING_URI}/suggested-switch`,
        params,
    ).then((switches) => {
        const s = switches && switches[0];
        return s ? {...s, id: getSuggestedSwitchId(s)} : null;
    });
}

export async function linkKmPost(
    geometryKmPostId: GeometryKmPostId,
    layoutKmPostId: LayoutKmPostId,
): Promise<string> {
    const params = {
        geometryKmPostId: geometryKmPostId,
        layoutKmPostId: layoutKmPostId,
    };
    const result = await postIgnoreError<typeof params, string>(
        `${LINKING_URI}/km-post-linking`,
        params,
    );
    if (!result) {
        throw Error('Failed to link km post!');
    }
    updateKmPostChangeTime();
    updatePlanChangeTime();
    return result;
}

export async function insertKmPost(
    kmPost: KmPostSaveRequest,
): Promise<Result<LayoutKmPostId, KmPostSaveError>> {
    const apiResult = await postAdt<KmPostSaveRequest, LayoutKmPostId>(
        `${LINKING_URI}/km-post`,
        kmPost,
        true,
    );
    updateKmPostChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function updateKmPost(
    id: LayoutKmPostId,
    kmPost: KmPostSaveRequest,
): Promise<Result<LayoutKmPostId, KmPostSaveError>> {
    const apiResult = await putAdt<KmPostSaveRequest, LayoutKmPostId>(
        `${LINKING_URI}/km-posts/${id}`,
        kmPost,
        true,
    );
    updateKmPostChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export const deleteDraftKmPost = async (
    id: LayoutKmPostId,
): Promise<Result<LayoutKmPostId, KmPostSaveError>> => {
    const apiResult = await deleteAdt<undefined, LayoutKmPostId>(
        `${LINKING_URI}/km-posts/${id}`,
        undefined,
        true,
    );
    updateKmPostChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
};

export async function updateEndPointType(
    locationTrackId: LocationTrackId,
    locationTrackTypeUpdateRequest: LocationTrackTypeUpdateRequest,
): Promise<LocationTrackId | null> {
    const apiResult = await putIgnoreError<LocationTrackTypeUpdateRequest, LocationTrackId>(
        `${LINKING_URI}/location-tracks/${locationTrackId}/endpoint`,
        locationTrackTypeUpdateRequest,
    );
    updateLocationTrackChangeTime();
    return apiResult;
}

export async function updateEndPointConnectedLocationTrack(
    locationTrackId: LocationTrackId,
    locationTrackEndPointIdUpdateRequest: LocationTrackEndPointConnectedUpdateRequest,
): Promise<LocationTrackId | null> {
    const apiResult = await putIgnoreError<LocationTrackTypeUpdateRequest, LocationTrackId>(
        `${LINKING_URI}/location-tracks/${locationTrackId}/endpoint-location-track`,
        locationTrackEndPointIdUpdateRequest,
    );
    updateLocationTrackChangeTime();
    return apiResult;
}
