import {
    LayoutKmPostId,
    LayoutSwitchId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import {
    API_URI,
    getNonNull,
    getNullable,
    postNonNull,
    putNonNull,
    queryParams,
} from 'api/api-fetch';
import {
    GeometryPlanLinkStatus,
    GeometrySwitchSuggestionResult,
    KmPostLinkingParameters,
    LinkingGeometryWithAlignmentParameters,
    LinkingGeometryWithEmptyAlignmentParameters,
    SuggestedSwitch,
    SuggestedSwitchesAtGridPoints,
    SwitchRelinkingValidationResult,
    TrackSwitchRelinkingResult,
} from 'linking/linking-model';
import {
    getChangeTimes,
    updateKmPostChangeTime,
    updateLocationTrackChangeTime,
    updatePlanChangeTime,
    updateReferenceLineChangeTime,
    updateSwitchChangeTime,
} from 'common/change-time-api';
import { LayoutBranch, LayoutContext, PublicationState, Range } from 'common/common-model';
import { asyncCache } from 'cache/cache';
import { GeometryAlignmentId, GeometryPlanId, GeometrySwitchId } from 'geometry/geometry-model';
import { getMaxTimestamp } from 'utils/date-utils';
import { pointString } from 'common/common-api';
import { Point } from 'model/geometry';
import { filterNotEmpty, indexIntoMap } from 'utils/array-utils';
import { contextInUri } from 'track-layout/track-layout-api';

const LINKING_URI = `${API_URI}/linking`;

const geometryElementsLinkedStatusCache = asyncCache<
    `${PublicationState}_${LayoutBranch}_${GeometryPlanId}`,
    GeometryPlanLinkStatus
>();
const relinkingSwitchValidationCache = asyncCache<
    `${LayoutBranch}_${LocationTrackId}`,
    SwitchRelinkingValidationResult[]
>();

type LinkingDataType = 'reference-lines' | 'location-tracks' | 'switches' | 'km-posts';
type LinkingType =
    | 'geometry'
    | 'empty-geometry'
    | 'suggested'
    | 'validate-relinking'
    | 'relink-switches';

function planLinkingUri(layoutContext: LayoutContext, id?: string): string {
    const base = `${LINKING_URI}/${contextInUri(layoutContext)}/plans`;
    const idSection = id ? `/${id}` : '';
    return `${base}/${idSection}`;
}

function linkingUri(
    layoutBranch: LayoutBranch,
    dataType: LinkingDataType,
    linkingType: LinkingType,
    id?: string,
): string {
    const base = `${LINKING_URI}/${layoutBranch.toLowerCase()}/${dataType}`;
    const idSection = id ? `/${id}` : '';
    return `${base}/${idSection}/${linkingType}`;
}

export const linkGeometryWithReferenceLine = async (
    layoutBranch: LayoutBranch,
    parameters: LinkingGeometryWithAlignmentParameters,
): Promise<ReferenceLineId> => {
    const response = await postNonNull<LinkingGeometryWithAlignmentParameters, ReferenceLineId>(
        linkingUri(layoutBranch, 'reference-lines', 'geometry'),
        parameters,
    );

    await updateReferenceLineChangeTime();

    return response;
};

export const linkGeometryWithLocationTrack = async (
    layoutBranch: LayoutBranch,
    parameters: LinkingGeometryWithAlignmentParameters,
): Promise<LocationTrackId> => {
    const response = await postNonNull<LinkingGeometryWithAlignmentParameters, LocationTrackId>(
        linkingUri(layoutBranch, 'location-tracks', 'geometry'),
        parameters,
    );

    await updateLocationTrackChangeTime();

    return response;
};

export const linkGeometryWithEmptyReferenceLine = async (
    layoutBranch: LayoutBranch,
    parameters: LinkingGeometryWithEmptyAlignmentParameters,
): Promise<ReferenceLineId> => {
    const response = await postNonNull<
        LinkingGeometryWithEmptyAlignmentParameters,
        ReferenceLineId
    >(linkingUri(layoutBranch, 'reference-lines', 'empty-geometry'), parameters);

    await updateReferenceLineChangeTime();

    return response;
};

export const linkGeometryWithEmptyLocationTrack = async (
    layoutBranch: LayoutBranch,
    parameters: LinkingGeometryWithEmptyAlignmentParameters,
): Promise<LocationTrackId> => {
    const response = await postNonNull<
        LinkingGeometryWithEmptyAlignmentParameters,
        LocationTrackId
    >(linkingUri(layoutBranch, 'location-tracks', 'empty-geometry'), parameters);

    await updateLocationTrackChangeTime();

    return response;
};

export async function updateReferenceLineGeometry(
    layoutBranch: LayoutBranch,
    id: ReferenceLineId,
    mRange: Range<number>,
): Promise<ReferenceLineId | undefined> {
    const result = await putNonNull<Range<number>, ReferenceLineId>(
        linkingUri(layoutBranch, 'reference-lines', 'geometry', id),
        mRange,
    );
    await updateReferenceLineChangeTime();
    return result;
}

export async function updateLocationTrackGeometry(
    layoutBranch: LayoutBranch,
    id: LocationTrackId,
    mRange: Range<number>,
): Promise<LocationTrackId | undefined> {
    const result = await putNonNull<Range<number>, LocationTrackId>(
        linkingUri(layoutBranch, 'location-tracks', 'geometry', id),
        mRange,
    );
    await updateLocationTrackChangeTime();
    return result;
}

export async function getLinkedAlignmentIdsInPlan(
    planId: GeometryPlanId,
    layoutContext: LayoutContext,
): Promise<GeometryAlignmentId[]> {
    return getPlanLinkStatus(planId, layoutContext).then((planStatus) =>
        planStatus.alignments.filter((a) => a.isLinked).map((a) => a.id),
    );
}

export async function getPlanLinkStatus(
    planId: GeometryPlanId,
    layoutContext: LayoutContext,
): Promise<GeometryPlanLinkStatus> {
    const changeTimes = getChangeTimes();
    const maxChangeTime = getMaxTimestamp(
        changeTimes.layoutReferenceLine,
        changeTimes.layoutLocationTrack,
        changeTimes.layoutSwitch,
        changeTimes.layoutKmPost,
    );

    return geometryElementsLinkedStatusCache.get(
        maxChangeTime,
        `${layoutContext.publicationState}_${layoutContext.branch}_${planId}`,
        () => getNonNull(`${planLinkingUri(layoutContext, planId)}/status`),
    );
}

export async function getPlanLinkStatuses(
    planIds: GeometryPlanId[],
    layoutContext: LayoutContext,
): Promise<GeometryPlanLinkStatus[]> {
    const changeTimes = getChangeTimes();
    const maxChangeTime = getMaxTimestamp(
        changeTimes.layoutReferenceLine,
        changeTimes.layoutLocationTrack,
        changeTimes.layoutSwitch,
        changeTimes.layoutKmPost,
    );

    return geometryElementsLinkedStatusCache
        .getMany(
            maxChangeTime,
            planIds,
            (planId) => `${layoutContext.publicationState}_${layoutContext.branch}_${planId}`,
            (planIds) =>
                getNonNull<GeometryPlanLinkStatus[]>(
                    `${planLinkingUri(layoutContext, undefined)}/status?ids=${planIds}`,
                ).then((tracks) => {
                    const trackMap = indexIntoMap(tracks);
                    return (id) => trackMap.get(id)!;
                }),
        )
        .then((tracks) => tracks.filter(filterNotEmpty));
}

function uncompressSuggestedSwitchesAtGridPoints(
    switches: SuggestedSwitchesAtGridPoints,
): (SuggestedSwitch | undefined)[] {
    return switches.gridSwitchIndices.map((i) =>
        i == null ? undefined : switches.suggestedSwitches[i],
    );
}

export async function getSuggestedSwitchesForLayoutSwitchPlacing(
    layoutBranch: LayoutBranch,
    points: Point[],
    switchId: LayoutSwitchId,
): Promise<(SuggestedSwitch | undefined)[]> {
    const params = queryParams({
        points: points.map((p) => pointString(p)),
        switchId,
    });
    return getNonNull<SuggestedSwitchesAtGridPoints>(
        `${linkingUri(layoutBranch, 'switches', 'suggested')}${params}`,
    ).then((switches) => uncompressSuggestedSwitchesAtGridPoints(switches));
}

export async function getSuggestedSwitchForGeometrySwitch(
    layoutBranch: LayoutBranch,
    geometrySwitchId: GeometrySwitchId,
): Promise<GeometrySwitchSuggestionResult | undefined> {
    return getSuggestedSwitch(layoutBranch, queryParams({ geometrySwitchId }));
}

function getSuggestedSwitch<SuggestedSwitchType>(
    layoutBranch: LayoutBranch,
    params: string,
): Promise<SuggestedSwitchType | undefined> {
    const uri = linkingUri(layoutBranch, 'switches', 'suggested');
    return getNullable<SuggestedSwitchType>(`${uri}${params}`);
}

export async function linkSwitch(
    layoutBranch: LayoutBranch,
    params: SuggestedSwitch,
    switchId: LayoutSwitchId,
): Promise<LayoutSwitchId> {
    const result = await postNonNull<SuggestedSwitch, LayoutSwitchId>(
        linkingUri(layoutBranch, 'switches', 'geometry', switchId),
        params,
    );

    await updateLocationTrackChangeTime();
    await updateSwitchChangeTime();

    return result;
}

export async function linkKmPost(
    layoutBranch: LayoutBranch,
    params: KmPostLinkingParameters,
): Promise<LayoutKmPostId> {
    const result = await postNonNull<typeof params, LayoutKmPostId>(
        linkingUri(layoutBranch, 'km-posts', 'geometry'),
        params,
    );

    await updateKmPostChangeTime();
    await updatePlanChangeTime();

    return result;
}

export async function validateLocationTrackSwitchRelinking(
    layoutBranch: LayoutBranch,
    locationTrackId: LocationTrackId,
): Promise<SwitchRelinkingValidationResult[]> {
    return relinkingSwitchValidationCache.get(
        getMaxTimestamp(getChangeTimes().layoutSwitch, getChangeTimes().layoutLocationTrack),
        `${layoutBranch}_${locationTrackId}`,
        () =>
            getNonNull<SwitchRelinkingValidationResult[]>(
                linkingUri(layoutBranch, 'location-tracks', 'validate-relinking', locationTrackId),
            ),
    );
}

export async function relinkTrackSwitches(
    layoutBranch: LayoutBranch,
    id: LocationTrackId,
): Promise<TrackSwitchRelinkingResult[]> {
    const rv = await postNonNull<null, TrackSwitchRelinkingResult[]>(
        linkingUri(layoutBranch, 'location-tracks', 'relink-switches', id),
        null,
    );
    await Promise.all([updateSwitchChangeTime(), updateLocationTrackChangeTime()]);
    return rv;
}
