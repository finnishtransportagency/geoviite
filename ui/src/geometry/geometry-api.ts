import { asyncCache } from 'cache/cache';
import {
    Author,
    GeometryAlignmentId,
    GeometryElement,
    GeometryElementId,
    ElementItem,
    GeometryPlan,
    GeometryPlanHeader,
    GeometryPlanId,
    GeometrySwitch,
    GeometrySwitchId,
    PlanSource,
    Project,
    SortByValue,
    SortOrderValue,
} from 'geometry/geometry-model';
import {
    GeometryPlanLayout,
    LayoutSwitch,
    LayoutTrackNumberId,
    LocationTrackId,
    MapAlignment,
    PlanArea,
} from 'track-layout/track-layout-model';
import {
    API_URI,
    getIgnoreError,
    getThrowError,
    getWithDefault,
    Page,
    postAdt,
    postIgnoreError,
    queryParams,
} from 'api/api-fetch';
import { BoundingBox } from 'model/geometry';
import { MapTile } from 'map/map-model';
import { getChangeTimes } from 'common/change-time-api';
import { TimeStamp } from 'common/common-model';
import { bboxString } from 'common/common-api';
import { filterNotEmpty } from 'utils/array-utils';
import { GeometryTypeIncludingMissing } from 'data-products/element-list/element-list-store';

export const GEOMETRY_URI = `${API_URI}/geometry`;

const trackLayoutPlanCache = asyncCache<GeometryPlanId, GeometryPlanLayout | null>();
const geometryPlanCache = asyncCache<GeometryPlanId, GeometryPlan | null>();
const geometryPlanAreaCache = asyncCache<GeometryPlanId, PlanArea[]>();

export async function getPlanAreasByTile(
    mapTile: MapTile,
    changeTime: TimeStamp,
): Promise<PlanArea[]> {
    return geometryPlanAreaCache.get(changeTime, mapTile.id, () => getPlanAreas(mapTile.area));
}

async function getPlanAreas(bbox: BoundingBox): Promise<PlanArea[]> {
    const params = queryParams({ bbox: bboxString(bbox) });
    const path = `${GEOMETRY_URI}/plans/areas${params}`;
    return (await getIgnoreError<PlanArea[]>(path)) || [];
}

export async function getGeometryPlanHeadersBySearchTerms(
    limit: number,
    offset?: number,
    bbox?: BoundingBox,
    sources?: PlanSource[],
    trackNumberIds?: LayoutTrackNumberId[],
    freeText?: string,
    sortField?: SortByValue,
    sortOrder?: SortOrderValue,
): Promise<Page<GeometryPlanHeader>> {
    const params = queryParams({
        bbox: bbox ? bboxString(bbox) : undefined,
        sources: sources,
        offset: offset || 0,
        limit: limit,
        freeText: freeText,
        trackNumberIds: trackNumberIds,
        sortField:
            !sortField || sortField === SortByValue.NO_SORTING ? undefined : SortByValue[sortField],
        sortOrder:
            !sortOrder || sortField === SortByValue.NO_SORTING
                ? undefined
                : SortOrderValue[sortOrder],
    });
    return getWithDefault<Page<GeometryPlanHeader>>(`${GEOMETRY_URI}/plan-headers${params}`, {
        totalCount: 0,
        items: [],
        start: 0,
    });
}

export async function getGeometryPlanHeader(planId: GeometryPlanId): Promise<GeometryPlanHeader> {
    return getThrowError<GeometryPlanHeader>(`${GEOMETRY_URI}/plan-headers/${planId}`);
}

export async function getGeometryPlanHeaders(
    planIds: GeometryPlanId[],
): Promise<GeometryPlanHeader[]> {
    return planIds.length > 0
        ? getThrowError<GeometryPlanHeader[]>(`${GEOMETRY_URI}/plan-headers?planIds=${planIds}`)
        : Promise.resolve([]);
}

export async function getGeometryPlanElements(
    planId: GeometryPlanId,
    elementTypes: GeometryTypeIncludingMissing[],
): Promise<ElementItem[] | null> {
    const params = queryParams({
        elementTypes,
    });
    return getIgnoreError(`${GEOMETRY_URI}/plans/${planId}/element-listing${params}`);
}

export const getGeometryPlanElementsCsv = (
    planId: GeometryPlanId,
    elementTypes: GeometryTypeIncludingMissing[],
) => `${GEOMETRY_URI}/plans/${planId}/element-listing/file${queryParams({ elementTypes })}`;

export async function getLocationTrackElements(
    id: LocationTrackId,
    elementTypes: GeometryTypeIncludingMissing[],
    startAddress: string | undefined,
    endAddress: string | undefined,
): Promise<ElementItem[] | null> {
    const params = queryParams({
        elementTypes: elementTypes,
        startAddress: startAddress,
        endAddress: endAddress,
    });
    return getIgnoreError(`${GEOMETRY_URI}/layout/location-tracks/${id}/element-listing${params}`);
}

export const getLocationTrackElementsCsv = (
    locationTrackId: LocationTrackId,
    elementTypes: GeometryTypeIncludingMissing[],
    startAddress: string | undefined,
    endAddress: string | undefined,
) => {
    const searchQueryParameters = queryParams({
        elementTypes,
        startAddress,
        endAddress,
    });
    return `${GEOMETRY_URI}/layout/location-tracks/${locationTrackId}/element-listing/file${searchQueryParameters}`;
};

export async function getGeometryPlan(
    planId: GeometryPlanId,
    changeTime: TimeStamp = getChangeTimes().geometryPlan,
): Promise<GeometryPlan | null> {
    return geometryPlanCache.get(changeTime, planId, () =>
        getIgnoreError(`${GEOMETRY_URI}/plans/${planId}`),
    );
}

export async function getGeometryPlansByIds(planIds: GeometryPlanId[]): Promise<GeometryPlan[]> {
    return Promise.all(planIds.map((planId) => getGeometryPlan(planId))).then((plans) =>
        plans.filter(filterNotEmpty),
    );
}

export async function getGeometryElement(
    elementId: GeometryElementId,
): Promise<GeometryElement | null> {
    return await getIgnoreError<GeometryElement>(`${GEOMETRY_URI}/plans/elements/${elementId}`);
}

export async function getGeometrySwitch(switchId: GeometrySwitchId): Promise<GeometrySwitch> {
    return await getThrowError<GeometrySwitch>(`${GEOMETRY_URI}/switches/${switchId}`);
}

export async function getGeometrySwitchLayout(
    switchId: GeometrySwitchId,
): Promise<LayoutSwitch | null> {
    return await getIgnoreError<LayoutSwitch | null>(`${GEOMETRY_URI}/switches/${switchId}/layout`);
}

export async function getTrackLayoutPlan(
    planId: GeometryPlanId,
    changeTime: TimeStamp,
    includeGeometryData = true,
): Promise<GeometryPlanLayout | null> {
    const url = `${GEOMETRY_URI}/plans/${planId}/layout?includeGeometryData=${includeGeometryData}`;
    const key = `${planId}-${includeGeometryData}`;
    return trackLayoutPlanCache.get(changeTime, key, () => getIgnoreError(url));
}

export async function getTrackLayoutPlans(
    planIds: GeometryPlanId[],
    includeGeometryData = true,
): Promise<GeometryPlanLayout[]> {
    const changeTime = getChangeTimes().geometryPlan;
    return Promise.all(
        planIds.map((planId) => getTrackLayoutPlan(planId, changeTime, includeGeometryData)),
    ).then((plans) => plans.filter(filterNotEmpty));
}

export async function getGeometryAlignmentLayout(
    planId: GeometryPlanId,
    geometryAlignmentId: GeometryAlignmentId,
    includeGeometryData = true,
    changeTime?: TimeStamp,
): Promise<MapAlignment | undefined> {
    return getTrackLayoutPlan(
        planId,
        changeTime || getChangeTimes().geometryPlan,
        includeGeometryData,
    ).then((plan) => {
        return plan?.alignments.find((alignment) => alignment.id === geometryAlignmentId);
    });
}

export async function fetchProjects(): Promise<Project[]> {
    return await getThrowError<Project[]>(`${GEOMETRY_URI}/projects`);
}

export async function createProject(project: Project): Promise<Project | null> {
    return await postIgnoreError<Project, Project>(`${GEOMETRY_URI}/projects`, project);
}

export async function fetchAuthors(): Promise<Author[]> {
    return await getThrowError<Author[]>(`${GEOMETRY_URI}/authors`);
}

export async function createAuthor(author: Author): Promise<Author | null> {
    return await postIgnoreError<Author, Author>(`${GEOMETRY_URI}/authors`, author);
}

export interface GeometryPlanLinkingSummary {
    linkedAt: Date;
    linkedByUsers: string;
}
export async function getGeometryPlanLinkingSummaries(
    planIds: GeometryPlanId[],
): Promise<{ [key: GeometryPlanId]: GeometryPlanLinkingSummary } | null> {
    const r = await postAdt<
        GeometryPlanId[],
        { [key: GeometryPlanId]: GeometryPlanLinkingSummary }
    >(`${GEOMETRY_URI}/plans/linking-summaries/`, planIds);
    return r.isOk() ? r.value : null;
}
