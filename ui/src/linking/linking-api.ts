import {
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutSwitchId,
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
    KmPostLinkingParameters,
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
import { PublishType, SwitchStructureId, Range } from 'common/common-model';
import { asyncCache } from 'cache/cache';
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { MapTile } from 'map/map-model';
import { getMaxTimestamp } from 'utils/date-utils';
import { getSuggestedSwitchId } from 'linking/linking-utils';
import { bboxString, pointString } from 'common/common-api';
import { BoundingBox, Point } from 'model/geometry';

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
): Promise<ReferenceLineId | null> => {
    const response = await postIgnoreError<LinkingGeometryWithAlignmentParameters, ReferenceLineId>(
        linkingUri('reference-lines', 'geometry'),
        parameters,
    );
    await updateReferenceLineChangeTime();
    return response;
};

export const linkGeometryWithLocationTrack = async (
    parameters: LinkingGeometryWithAlignmentParameters,
): Promise<LocationTrackId | null> => {
    const response = await postIgnoreError<LinkingGeometryWithAlignmentParameters, LocationTrackId>(
        linkingUri('location-tracks', 'geometry'),
        parameters,
    );
    await updateLocationTrackChangeTime();
    return response;
};

export const linkGeometryWithEmptyReferenceLine = async (
    parameters: LinkingGeometryWithEmptyAlignmentParameters,
): Promise<ReferenceLineId | null> => {
    const response = await postIgnoreError<
        LinkingGeometryWithEmptyAlignmentParameters,
        ReferenceLineId
    >(linkingUri('reference-lines', 'empty-geometry'), parameters);
    await updateReferenceLineChangeTime();
    return response;
};

export const linkGeometryWithEmptyLocationTrack = async (
    parameters: LinkingGeometryWithEmptyAlignmentParameters,
): Promise<LocationTrackId | null> => {
    const response = await postIgnoreError<
        LinkingGeometryWithEmptyAlignmentParameters,
        LocationTrackId
    >(linkingUri('location-tracks', 'empty-geometry'), parameters);
    await updateLocationTrackChangeTime();
    return response;
};

export async function updateReferenceLineGeometry(
    id: ReferenceLineId,
    mRange: Range<number>,
): Promise<ReferenceLineId | null> {
    const result = await putIgnoreError<Range<number>, ReferenceLineId>(
        linkingUri('reference-lines', 'geometry', id),
        mRange,
    );
    await updateReferenceLineChangeTime();
    return result;
}

export async function updateLocationTrackGeometry(
    id: LocationTrackId,
    mRange: Range<number>,
): Promise<LocationTrackId | null> {
    const result = await putIgnoreError<Range<number>, LocationTrackId>(
        linkingUri('location-tracks', 'geometry', id),
        mRange,
    );
    await updateLocationTrackChangeTime();
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
        return (suggestedSwitches || []).map((suggestedSwitch) => {
            return {
                ...suggestedSwitch,
                id: getSuggestedSwitchId(suggestedSwitch),
            };
        });
    });
}

export async function linkSwitch(params: SwitchLinkingParameters): Promise<LayoutSwitchId> {
    const result = await postIgnoreError<SwitchLinkingParameters, LayoutSwitchId>(
        linkingUri('switches', 'geometry'),
        params,
    );
    if (!result) {
        throw Error('Failed to link switch!');
    }
    await updateLocationTrackChangeTime();
    await updateSwitchChangeTime();
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

export async function linkKmPost(params: KmPostLinkingParameters): Promise<LayoutKmPostId> {
    const result = await postIgnoreError<typeof params, LayoutKmPostId>(
        linkingUri('km-posts', 'geometry'),
        params,
    );
    if (!result) {
        throw Error('Failed to link km post!');
    }
    await updateKmPostChangeTime();
    await updatePlanChangeTime();
    return result;
}
