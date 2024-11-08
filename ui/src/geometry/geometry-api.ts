import { asyncCache } from 'cache/cache';
import {
    Author,
    ElementItem,
    GeometryAlignmentId,
    GeometryElement,
    GeometryElementId,
    GeometryPlan,
    GeometryPlanHeader,
    GeometryPlanId,
    GeometrySortBy,
    GeometrySortOrder,
    GeometrySwitch,
    GeometrySwitchId,
    PlanSource,
    Project,
    ProjectId,
    VerticalGeometryItem,
} from 'geometry/geometry-model';
import {
    AlignmentStartAndEnd,
    GeometryPlanLayout,
    LayoutSwitch,
    LocationTrackId,
    PlanArea,
} from 'track-layout/track-layout-model';
import {
    API_URI,
    getNonNull,
    getNullable,
    Page,
    postNonNull,
    postNullable,
    queryParams,
} from 'api/api-fetch';
import { BoundingBox, Point } from 'model/geometry';
import { MapTile } from 'map/map-model';
import { getChangeTimes } from 'common/change-time-api';
import {
    ElevationMeasurementMethod,
    KmNumber,
    LayoutBranch,
    LayoutContext,
    officialMainLayoutContext,
    PublicationState,
    TimeStamp,
    TrackNumber,
    VerticalCoordinateSystem,
} from 'common/common-model';
import { bboxString } from 'common/common-api';
import { filterNotEmpty } from 'utils/array-utils';
import { GeometryTypeIncludingMissing } from 'data-products/data-products-slice';
import { AlignmentHeader } from 'track-layout/layout-map-api';
import i18next from 'i18next';
import { contextInUri } from 'track-layout/track-layout-api';

export const GEOMETRY_URI = `${API_URI}/geometry`;

const trackLayoutPlanCache = asyncCache<GeometryPlanId, GeometryPlanLayout | undefined>();
const geometryPlanCache = asyncCache<GeometryPlanId, GeometryPlan | undefined>();
const geometryPlanAreaCache = asyncCache<string, PlanArea[]>(); // map tile ID => plan area[]

const projectCache = asyncCache<undefined, Project[]>();

type PlanVerticalGeometryKey = GeometryPlanId;
const planVerticalGeometryCache = asyncCache<
    PlanVerticalGeometryKey,
    VerticalGeometryItem[] | undefined
>;
type LocationTrackVerticalGeometryKey = `${LocationTrackId}_${PublicationState}_${LayoutBranch}`;
const locationTrackVerticalGeometryCache = asyncCache<
    LocationTrackVerticalGeometryKey,
    VerticalGeometryItem[] | undefined
>();

export async function getPlanAreasByTile(
    mapTile: MapTile,
    changeTime: TimeStamp,
): Promise<PlanArea[]> {
    return (
        (await geometryPlanAreaCache.get(changeTime, mapTile.id, () =>
            getPlanAreas(mapTile.area),
        )) || []
    );
}

async function getPlanAreas(bbox: BoundingBox): Promise<PlanArea[]> {
    const params = queryParams({ bbox: bboxString(bbox) });
    const path = `${GEOMETRY_URI}/plans/areas${params}`;
    return getNonNull<PlanArea[]>(path);
}
export interface GeometryPlanHeadersSearchResult {
    planHeaders: Page<GeometryPlanHeader>;
    remainingIds: GeometryPlanId[];
}

export async function getGeometryPlanHeadersBySearchTerms(
    limit: number,
    offset?: number,
    bbox?: BoundingBox,
    sources?: PlanSource[],
    trackNumbers?: TrackNumber[],
    freeText?: string,
    sortField?: GeometrySortBy,
    sortOrder?: GeometrySortOrder,
): Promise<GeometryPlanHeadersSearchResult> {
    const params = queryParams({
        bbox: bbox ? bboxString(bbox) : undefined,
        sources: sources,
        offset: offset || 0,
        limit: limit,
        freeText: freeText,
        trackNumbers,
        sortField:
            sortField === undefined || sortField === GeometrySortBy.NO_SORTING
                ? undefined
                : GeometrySortBy[sortField],
        sortOrder:
            typeof sortOrder === 'undefined' || sortField === GeometrySortBy.NO_SORTING
                ? undefined
                : GeometrySortOrder[sortOrder],
        lang: i18next.language,
    });

    return getNonNull<GeometryPlanHeadersSearchResult>(`${GEOMETRY_URI}/plan-headers${params}`);
}

export async function getGeometryPlanHeader(planId: GeometryPlanId): Promise<GeometryPlanHeader> {
    return getNonNull<GeometryPlanHeader>(`${GEOMETRY_URI}/plan-headers/${planId}`);
}

export async function getGeometryPlanHeaders(
    planIds: GeometryPlanId[],
): Promise<GeometryPlanHeader[]> {
    return planIds.length > 0
        ? getNonNull<GeometryPlanHeader[]>(`${GEOMETRY_URI}/plan-headers?planIds=${planIds}`)
        : Promise.resolve([]);
}

export async function getGeometryPlanElements(
    planId: GeometryPlanId,
    elementTypes: GeometryTypeIncludingMissing[],
): Promise<ElementItem[] | undefined> {
    const params = queryParams({
        elementTypes,
    });
    return getNullable(`${GEOMETRY_URI}/plans/${planId}/element-listing${params}`);
}

export const getGeometryPlanElementsCsv = (
    planId: GeometryPlanId,
    elementTypes: GeometryTypeIncludingMissing[],
) =>
    `${GEOMETRY_URI}/plans/${planId}/element-listing/file${queryParams({ elementTypes, lang: i18next.language })}`;

export const getEntireRailNetworkElementsCsvUrl = () =>
    `${GEOMETRY_URI}/rail-network/element-listing/file`;

export async function getLocationTrackElements(
    id: LocationTrackId,
    elementTypes: GeometryTypeIncludingMissing[],
    startAddress: string | undefined,
    endAddress: string | undefined,
): Promise<ElementItem[] | undefined> {
    const params = queryParams({
        elementTypes: elementTypes,
        startAddress: startAddress,
        endAddress: endAddress,
    });
    return getNonNull(
        `${geometryLayoutPath(officialMainLayoutContext())}/location-tracks/${id}/element-listing${params}`,
    );
}

export async function getLocationTrackVerticalGeometry(
    changeTime: TimeStamp | undefined,
    layoutContext: LayoutContext,
    id: LocationTrackId,
    startAddress: string | undefined,
    endAddress: string | undefined,
): Promise<VerticalGeometryItem[] | undefined> {
    const params = queryParams({
        startAddress: startAddress,
        endAddress: endAddress,
    });
    const fetch: () => Promise<VerticalGeometryItem[] | undefined> = () =>
        getNonNull(
            `${geometryLayoutPath(layoutContext)}/location-tracks/${id}/vertical-geometry${params}`,
        );
    return changeTime === undefined
        ? fetch()
        : locationTrackVerticalGeometryCache.get(
              changeTime,
              `${id}_${layoutContext.publicationState}_${layoutContext.branch}`,
              fetch,
          );
}

export async function getGeometryPlanVerticalGeometry(
    changeTime: TimeStamp | undefined,
    planId: GeometryPlanId,
): Promise<VerticalGeometryItem[] | undefined> {
    const fetch: () => Promise<VerticalGeometryItem[]> = () =>
        getNonNull(`${GEOMETRY_URI}/plans/${planId}/vertical-geometry`);
    return changeTime === undefined
        ? fetch()
        : planVerticalGeometryCache().get(changeTime, planId, fetch);
}

export const getLocationTrackVerticalGeometryCsv = (
    trackId: LocationTrackId,
    startAddress: string | undefined,
    endAddress: string | undefined,
) => {
    const params = queryParams({
        startAddress: startAddress,
        endAddress: endAddress,
        lang: i18next.language,
    });
    return `${GEOMETRY_URI}/layout/location-tracks/${trackId}/vertical-geometry/file${params}`;
};

export const getGeometryPlanVerticalGeometryCsv = (planId: GeometryPlanId) =>
    `${GEOMETRY_URI}/plans/${planId}/vertical-geometry/file${queryParams({ lang: i18next.language })}`;

export const getEntireRailNetworkVerticalGeometryCsvUrl = () =>
    `${GEOMETRY_URI}/rail-network/vertical-geometry/file`;

export const geometryLayoutPath = (context: LayoutContext): string =>
    `${GEOMETRY_URI}/layout/${contextInUri(context)}`;

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
        lang: i18next.language,
    });
    return `${geometryLayoutPath(officialMainLayoutContext())}/location-tracks/${locationTrackId}/element-listing/file${searchQueryParameters}`;
};

export async function getGeometryPlan(
    planId: GeometryPlanId,
    changeTime: TimeStamp = getChangeTimes().geometryPlan,
): Promise<GeometryPlan | undefined> {
    return geometryPlanCache.get(changeTime, planId, () =>
        getNullable(`${GEOMETRY_URI}/plans/${planId}`),
    );
}

export async function getGeometryPlansByIds(planIds: GeometryPlanId[]): Promise<GeometryPlan[]> {
    return Promise.all(planIds.map((planId) => getGeometryPlan(planId))).then((plans) =>
        plans.filter(filterNotEmpty),
    );
}

export async function getGeometryElement(
    elementId: GeometryElementId,
): Promise<GeometryElement | undefined> {
    return await getNullable<GeometryElement>(`${GEOMETRY_URI}/plans/elements/${elementId}`);
}

export async function getGeometrySwitch(switchId: GeometrySwitchId): Promise<GeometrySwitch> {
    return await getNonNull<GeometrySwitch>(`${GEOMETRY_URI}/switches/${switchId}`);
}

export async function getGeometrySwitchLayout(
    switchId: GeometrySwitchId,
): Promise<LayoutSwitch | undefined> {
    return await getNullable<LayoutSwitch>(`${GEOMETRY_URI}/switches/${switchId}/layout`);
}

export async function getTrackLayoutPlansByIds(
    planIds: GeometryPlanId[],
    changeTime: TimeStamp,
    includeGeometryData = true,
): Promise<GeometryPlanLayout[]> {
    return Promise.all(
        planIds.map((planId) => getTrackLayoutPlan(planId, changeTime, includeGeometryData)),
    ).then((plans) => plans.filter(filterNotEmpty));
}

export async function getTrackLayoutPlan(
    planId: GeometryPlanId,
    changeTime: TimeStamp,
    includeGeometryData = true,
): Promise<GeometryPlanLayout | undefined> {
    const url = `${GEOMETRY_URI}/plans/${planId}/layout?includeGeometryData=${includeGeometryData}`;
    const key = `${planId}-${includeGeometryData}`;
    return trackLayoutPlanCache.get(changeTime, key, () => getNullable(url));
}

export async function getTrackLayoutPlans(
    planIds: GeometryPlanId[],
    changeTime: TimeStamp,
    includeGeometryData = true,
): Promise<GeometryPlanLayout[]> {
    return Promise.all(
        planIds.map((planId) => getTrackLayoutPlan(planId, changeTime, includeGeometryData)),
    ).then((layouts) => layouts.filter(filterNotEmpty));
}

export async function getProjects(changeTime = getChangeTimes().project): Promise<Project[]> {
    return projectCache.get(changeTime, undefined, () =>
        getNonNull<Project[]>(`${GEOMETRY_URI}/projects`),
    );
}

export async function getProject(id: ProjectId): Promise<Project> {
    return getProjects().then((projects) => {
        const project = projects.find((project) => project.id === id);
        if (!project) {
            throw new Error(`Couldn't find project ${id}`);
        }
        return project;
    });
}

export async function createProject(project: Project): Promise<ProjectId> {
    return await postNonNull<Project, ProjectId>(`${GEOMETRY_URI}/projects`, project);
}

export async function fetchAuthors(): Promise<Author[]> {
    return await getNonNull<Author[]>(`${GEOMETRY_URI}/authors`);
}

export async function createAuthor(author: Author): Promise<Author> {
    return await postNonNull<Author, Author>(`${GEOMETRY_URI}/authors`, author);
}

export interface GeometryPlanLinkingSummary {
    linkedAt?: Date;
    linkedByUsers: string[];
    currentlyLinked: boolean;
}

export function getGeometryPlanLinkingSummaries(
    planIds: GeometryPlanId[],
): Promise<{ [key: GeometryPlanId]: GeometryPlanLinkingSummary } | undefined> {
    return postNullable<GeometryPlanId[], { [key: GeometryPlanId]: GeometryPlanLinkingSummary }>(
        `${GEOMETRY_URI}/plans/linking-summaries`,
        planIds,
    );
}

export interface AlignmentHeights {
    kmHeights: TrackKmHeights[];
    alignmentStartM: number;
    alignmentEndM: number;
}

export interface TrackMeterHeight {
    /** m-value in entire alignment */
    m: number;
    meter: number;
    height?: number;
    point: Point;
}

export interface PlanLinkingSummaryItem {
    startM: number;
    endM: number;
    filename?: string;
    alignmentHeader?: AlignmentHeader;
    planId?: GeometryPlanId;
    verticalCoordinateSystem?: VerticalCoordinateSystem;
    elevationMeasurementMethod?: ElevationMeasurementMethod;
}

export interface TrackKmHeights {
    kmNumber: KmNumber;
    trackMeterHeights: TrackMeterHeight[];
    endM: number;
}

export async function getPlanAlignmentHeights(
    planId: GeometryPlanId,
    alignmentId: GeometryAlignmentId,
    startDistance: number,
    endDistance: number,
    tickLength: number,
): Promise<TrackKmHeights[]> {
    return getNonNull(
        `${GEOMETRY_URI}/plans/${planId}/plan-alignment-heights/${alignmentId}` +
            queryParams({ startDistance, endDistance, tickLength }),
    );
}

export async function getPlanAlignmentStartAndEnd(
    planId: GeometryPlanId,
    alignmentId: GeometryAlignmentId,
): Promise<AlignmentStartAndEnd | undefined> {
    return getNullable<AlignmentStartAndEnd>(
        `${GEOMETRY_URI}/plans/${planId}/start-and-end/${alignmentId}`,
    );
}

export async function getLocationTrackHeights(
    locationTrackId: LocationTrackId,
    layoutContext: LayoutContext,
    startDistance: number,
    endDistance: number,
    tickLength: number,
): Promise<TrackKmHeights[]> {
    return getNonNull(
        `${geometryLayoutPath(layoutContext)}/location-tracks/${locationTrackId}/alignment-heights` +
            queryParams({ startDistance, endDistance, tickLength }),
    ).catch(() => []) as Promise<TrackKmHeights[]>;
}

const locationTrackLinkingSummaryCache = asyncCache<
    `${LocationTrackId}_${PublicationState}_${LayoutBranch}`,
    PlanLinkingSummaryItem[]
>();

export async function getLocationTrackLinkingSummary(
    changeTime: TimeStamp,
    locationTrackId: LocationTrackId,
    layoutContext: LayoutContext,
): Promise<PlanLinkingSummaryItem[]> {
    return locationTrackLinkingSummaryCache.get(
        changeTime,
        `${locationTrackId}_${layoutContext.publicationState}_${layoutContext.branch}`,
        () =>
            getNonNull(
                `${geometryLayoutPath(layoutContext)}/location-tracks/${locationTrackId}/linking-summary`,
            ),
    );
}
