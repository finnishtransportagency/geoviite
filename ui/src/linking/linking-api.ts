import {
    LayoutKmPostId,
    LayoutLocationTrack,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import {
    API_URI,
    getIgnoreError,
    getThrowError,
    postIgnoreError,
    putIgnoreError,
    queryParams,
} from 'api/api-fetch';
import {
    GeometryPlanLinkStatus,
    IntervalRequest,
    LinkingGeometryWithAlignmentParameters,
    LinkingGeometryWithEmptyAlignmentParameters,
    LocationTrackEndpoint,
    SuggestedSwitch,
    SuggestedSwitchCreateParams,
    SwitchLinkingParameters,
} from 'linking/linking-model';
import {
    getChangeTimes,
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

type LinkingDataType = 'reference-lines' | 'location-tracks' | 'switches' | 'km-posts';
type LinkingType = 'geometry' | 'empty-geometry' | 'suggested';

function linkingUri(dataType: LinkingDataType, linkingType: LinkingType, id?: string): string {
    return id
        ? `${LINKING_URI}/${dataType}/${id}/${linkingType}`
        : `${LINKING_URI}/${dataType}/${linkingType}`;
}

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
    const uri = linkingUri('location-tracks', 'suggested');
    return getThrowError<LayoutLocationTrack[]>(`${uri}${params}`);
};

export const linkGeometryWithReferenceLine = async (
    parameters: LinkingGeometryWithAlignmentParameters,
): Promise<RowVersion | null> => {
    const response = await postIgnoreError<LinkingGeometryWithAlignmentParameters, RowVersion>(
        linkingUri('reference-lines', 'geometry'),
        parameters,
    );
    updateReferenceLineChangeTime();
    return response;
};

export const linkGeometryWithLocationTrack = async (
    parameters: LinkingGeometryWithAlignmentParameters,
): Promise<RowVersion | null> => {
    const response = await postIgnoreError<LinkingGeometryWithAlignmentParameters, RowVersion>(
        linkingUri('location-tracks', 'geometry'),
        parameters,
    );
    updateLocationTrackChangeTime();
    return response;
};

export const linkGeometryWithEmptyReferenceLine = async (
    parameters: LinkingGeometryWithEmptyAlignmentParameters,
): Promise<RowVersion | null> => {
    const response = await postIgnoreError<LinkingGeometryWithEmptyAlignmentParameters, RowVersion>(
        linkingUri('reference-lines', 'empty-geometry'),
        parameters,
    );
    updateReferenceLineChangeTime();
    return response;
};

export const linkGeometryWithEmptyLocationTrack = async (
    parameters: LinkingGeometryWithEmptyAlignmentParameters,
): Promise<RowVersion | null> => {
    const response = await postIgnoreError<LinkingGeometryWithEmptyAlignmentParameters, RowVersion>(
        linkingUri('location-tracks', 'empty-geometry'),
        parameters,
    );
    updateLocationTrackChangeTime();
    return response;
};

export async function updateReferenceLineGeometry(
    id: ReferenceLineId,
    interval: IntervalRequest,
): Promise<string | null> {
    const result = await putIgnoreError<IntervalRequest, ReferenceLineId>(
        linkingUri('reference-lines', 'geometry', id),
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
        linkingUri('location-tracks', 'geometry', id),
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
                () => getThrowError(`${linkingUri('switches', 'suggested')}${params}`),
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

export async function getSuggestedSwitchByPoint(
    point: Point,
    switchStructureId: SwitchStructureId,
): Promise<SuggestedSwitch[]> {
    const params = queryParams({
        location: pointString(point),
        switchStructureId: switchStructureId,
    });
    const uri = linkingUri('switches', 'suggested');
    return getIgnoreError<SuggestedSwitch[]>(`${uri}${params}`).then((suggestedSwitches) => {
        return (suggestedSwitches || []).filter(filterNotEmpty).map((suggestedSwitch) => {
            return {
                ...suggestedSwitch,
                id: getSuggestedSwitchId(suggestedSwitch),
            };
        });
    });
}

export async function linkSwitch(params: SwitchLinkingParameters): Promise<string> {
    const result = await postIgnoreError<SwitchLinkingParameters, string>(
        linkingUri('switches', 'geometry'),
        params,
    );
    if (!result) {
        throw Error('Failed to link switch!');
    }
    updateLocationTrackChangeTime();
    updateSwitchChangeTime();
    return result;
}

export async function createSuggestedSwitch(
    params: SuggestedSwitchCreateParams,
): Promise<SuggestedSwitch | null> {
    return postIgnoreError<SuggestedSwitchCreateParams, SuggestedSwitch[]>(
        linkingUri('switches', 'suggested'),
        params,
    ).then((switches) => {
        const s = switches && switches[0];
        return s ? { ...s, id: getSuggestedSwitchId(s) } : null;
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
        linkingUri('km-posts', 'geometry'),
        params,
    );
    if (!result) {
        throw Error('Failed to link km post!');
    }
    updateKmPostChangeTime();
    updatePlanChangeTime();
    return result;
}
