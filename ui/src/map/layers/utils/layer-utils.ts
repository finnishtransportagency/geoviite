import Feature from 'ol/Feature';
import { Coordinate } from 'ol/coordinate';
import { Geometry, LineString, Point as OlPoint, Polygon } from 'ol/geom';
import { GeometryPlanLayout, LAYOUT_SRID, PlanAndStatus } from 'track-layout/track-layout-model';
import { VisiblePlanLayout } from 'selection/selection-model';
import { LayerItemSearchResult, SearchItemsOptions } from 'map/layers/utils/layer-model';
import proj4 from 'proj4';
import { coordsToPoint, Point, Rectangle } from 'model/geometry';
import { register } from 'ol/proj/proj4';
import VectorSource from 'ol/source/Vector';
import { avg, filterNotEmpty, filterUnique } from 'utils/array-utils';
import { distToSegmentSquared } from 'utils/math-utils';
import { getPlanLinkStatus, getPlanLinkStatuses } from 'linking/linking-api';
import { getPlanAreasByTile, getTrackLayoutPlans } from 'geometry/geometry-api';
import { ChangeTimes } from 'common/common-slice';
import { MapLayerName, MapTile } from 'map/map-model';
import VectorLayer from 'ol/layer/Vector';
import BaseLayer from 'ol/layer/Base';
import { expectCoordinate, tuple } from 'utils/type-utils';
import { LayoutContext } from 'common/common-model';

export type GeoviiteMapLayer<T extends Geometry> = VectorLayer<VectorSource<Feature<T>>>;

proj4.defs(LAYOUT_SRID, '+proj=utm +zone=35 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs');
register(proj4);

/**
 * Returns the centroid of the polygon. The centroid is not the
 * same as the visual center of a polygon, but the centroid is faster
 * to calculate and suitable for most needs.
 *
 * @param polygon
 */
export function centroid(polygon: Polygon): OlPoint {
    const points = polygon
        .getLinearRing(0)
        ?.getCoordinates()
        ?.map((coordinate) => coordsToPoint(coordinate));

    if (!points) {
        throw 'Cannot find the center of a polygon!';
    }

    const x = avg(points.map((point) => point.x));
    const y = avg(points.map((point) => point.y));
    return new OlPoint([x, y]);
}

/**
 * Return a distance between two point in meters
 *
 * @param pointA
 * @param pointB
 */
function getPlanarDistancePointAndPoint(pointA: OlPoint, pointB: OlPoint): number {
    const [aX, aY] = expectCoordinate(pointA.getCoordinates());
    const [bX, bY] = expectCoordinate(pointB.getCoordinates());

    return getPlanarDistanceUnwrapped(aX, aY, bX, bY);
}

export function getPlanarDistanceUnwrapped(x1: number, y1: number, x2: number, y2: number): number {
    return Math.hypot(x1 - x2, y1 - y2);
}

/**
 * Return the shortest distance between the point and the line in meters
 *
 * @param olPoint
 * @param line
 */
export function getDistancePointAndLine(olPoint: OlPoint, line: LineString): number {
    const point = coordsToPoint(olPoint.getCoordinates());
    const segments = line
        .getCoordinates()
        .map((coordinate) => coordsToPoint(coordinate))
        .map((point, index, points) => {
            const nextPoint = points[index + 1];
            return nextPoint ? tuple(point, nextPoint) : undefined;
        })
        .filter(filterNotEmpty);
    const squaredDistances = segments.map((segment) =>
        distToSegmentSquared(point, segment[0], segment[1]),
    );
    const minSquaredDistance = Math.min(...squaredDistances);
    return Math.sqrt(minSquaredDistance);
}

export function getDistancePointAndPolygon(point: OlPoint, polygon: Polygon): number {
    const polyCenter = centroid(polygon);
    return getPlanarDistancePointAndPoint(point, polyCenter);
}

/**
 * Return the shortest distance between the point and the geometry in meters
 *
 * @param point
 * @param geom
 */
export function getDistance(point: OlPoint, geom: Geometry): number {
    if (geom instanceof OlPoint) {
        return getPlanarDistancePointAndPoint(point, geom);
    } else if (geom instanceof LineString) {
        return getDistancePointAndLine(point, geom);
    } else if (geom instanceof Polygon) {
        return getDistancePointAndPolygon(point, geom);
    }
    throw `Unsupported geometry type in "getDistance"`;
}

export function findMatchingEntities<T>(
    hitArea: Rectangle,
    source: VectorSource,
    propertyName: string,
    options?: SearchItemsOptions,
): T[] {
    const entities = findIntersectingFeatures(hitArea, source)
        .map((f) => f.get(propertyName) as T | undefined)
        .filter(filterNotEmpty);

    if (options?.limit && options.limit > 0) return entities.slice(0, options.limit);
    else return entities;
}

export function findIntersectingFeatures<T extends Geometry>(
    hitArea: Rectangle,
    source: VectorSource,
): Feature<T>[] {
    const features: Feature<T>[] = [];

    source.forEachFeatureIntersectingExtent(hitArea.getExtent(), (f: Feature<T>) => {
        features.push(f);
    });

    return sortFeaturesByDistance(features, centroid(hitArea));
}

export function sortFeaturesByDistance<T extends Geometry>(features: Feature<T>[], point: OlPoint) {
    return features.sort((featureA, featureB) => {
        const geometryA = featureA.getGeometry();
        const geometryB = featureB.getGeometry();

        if (geometryA && geometryB)
            return getDistance(point, geometryA) - getDistance(point, geometryB);
        else if (geometryA) return -1;
        else if (geometryB) return 1;
        else return 0;
    });
}

const latestLayerIds: Map<MapLayerName, number> = new Map();
const incrementAndGetLayerId = (name: MapLayerName) => {
    const id = (latestLayerIds.get(name) ?? 0) + 1;
    latestLayerIds.set(name, id);
    return id;
};
const isLatestLayerId = (name: MapLayerName, id: number) => latestLayerIds.get(name) === id;

export type LayerResult<FeatureType extends Geometry> = {
    layer: BaseLayer;
    source: VectorSource<Feature<FeatureType>>;
    isLatest: () => boolean;
};

export function createLayer<FeatureType extends Geometry>(
    name: MapLayerName,
    existingLayer: GeoviiteMapLayer<FeatureType> | undefined,
    allowDefaultStyle: boolean = true,
    declutter: boolean = false,
): LayerResult<FeatureType> {
    const id = incrementAndGetLayerId(name);
    const source = existingLayer?.getSource() || new VectorSource();
    const options = allowDefaultStyle ? { source: source } : { source: source, style: null };
    const layer =
        existingLayer ||
        new VectorLayer({
            ...options,
            declutter: declutter,
        });
    return {
        layer,
        source,
        isLatest: () => isLatestLayerId(name, id),
    };
}

export function loadLayerData<Data, FeatureType extends Geometry>(
    source: VectorSource<Feature<FeatureType>>,
    isLatest: () => boolean,
    onLoadingData: (loading: boolean, loadedData: Data | undefined) => void,
    dataPromise: Promise<Data>,
    createFeatures: (data: Data) => Feature<FeatureType>[],
) {
    onLoadingData(true, undefined);
    dataPromise
        .then((data) => {
            if (isLatest()) {
                clearFeatures(source);
                const features = createFeatures(data);
                if (features.length > 0) source.addFeatures(features);
                onLoadingData(false, data);
            }
        })
        .catch((e) => {
            if (isLatest()) {
                console.error(e);
                clearFeatures(source);
                onLoadingData(false, undefined);
            }
        });
}

export const clearFeatures = (vectorSource: VectorSource) => vectorSource.clear();

function mergeOptionalArrays<T>(a1: T[] | undefined, a2: T[] | undefined): T[] | undefined {
    if (a1 === undefined) return a2;
    if (a2 === undefined) return a1;
    else return [...a1, ...a2];
}

export function mergePartialItemSearchResults(
    ...searchResults: LayerItemSearchResult[]
): LayerItemSearchResult {
    return searchResults.reduce<LayerItemSearchResult>((merged, searchResult) => {
        return {
            locationTracks: mergeOptionalArrays(merged.locationTracks, searchResult.locationTracks),
            kmPosts: mergeOptionalArrays(merged.kmPosts, searchResult.kmPosts),
            geometryKmPostIds: mergeOptionalArrays(
                merged.geometryKmPostIds,
                searchResult.geometryKmPostIds,
            ),
            switches: mergeOptionalArrays(merged.switches, searchResult.switches),
            geometrySwitchIds: mergeOptionalArrays(
                merged.geometrySwitchIds,
                searchResult.geometrySwitchIds,
            ),
            trackNumbers: mergeOptionalArrays(merged.trackNumbers, searchResult.trackNumbers),
            geometryAlignmentIds: mergeOptionalArrays(
                merged.geometryAlignmentIds,
                searchResult.geometryAlignmentIds,
            ),
            layoutLinkPoints: mergeOptionalArrays(
                merged.layoutLinkPoints,
                searchResult.layoutLinkPoints,
            ),
            geometryLinkPoints: mergeOptionalArrays(
                merged.geometryLinkPoints,
                searchResult.geometryLinkPoints,
            ),
            clusterPoints: mergeOptionalArrays(merged.clusterPoints, searchResult.clusterPoints),
            geometryPlans: mergeOptionalArrays(merged.geometryPlans, searchResult.geometryPlans),
            operationalPoints: mergeOptionalArrays(
                merged.operationalPoints,
                searchResult.operationalPoints,
            ),
            locationTrackPublicationCandidates: mergeOptionalArrays(
                merged.locationTrackPublicationCandidates,
                searchResult.locationTrackPublicationCandidates,
            ),
            referenceLinePublicationCandidates: mergeOptionalArrays(
                merged.referenceLinePublicationCandidates,
                searchResult.referenceLinePublicationCandidates,
            ),
            trackNumberPublicationCandidates: mergeOptionalArrays(
                merged.trackNumberPublicationCandidates,
                searchResult.trackNumberPublicationCandidates,
            ),
            switchPublicationCandidates: mergeOptionalArrays(
                merged.switchPublicationCandidates,
                searchResult.switchPublicationCandidates,
            ),
            kmPostPublicationCandidates: mergeOptionalArrays(
                merged.kmPostPublicationCandidates,
                searchResult.kmPostPublicationCandidates,
            ),
        };
    }, {});
}

export function pointToCoords(point: Point): Coordinate {
    return [point.x, point.y];
}

export async function getManualPlanWithStatus(
    plan: GeometryPlanLayout,
    layoutContext: LayoutContext,
): Promise<PlanAndStatus[]> {
    return getPlanAndStatus(plan, layoutContext).then((status) => (status ? [status] : []));
}

async function getTilesPlanIds(
    visiblePlans: VisiblePlanLayout[],
    mapTiles: MapTile[],
    changeTimes: ChangeTimes,
) {
    const visibleIds = new Set(visiblePlans.map((vp) => vp.id));
    return (
        await Promise.all(
            mapTiles.map((tile) => getPlanAreasByTile(tile, changeTimes.geometryPlan)),
        )
    )
        .flat()
        .map((plan) => plan.id)
        .filter((planId) => visibleIds.has(planId))
        .filter(filterUnique);
}

export async function getVisiblePlansWithStatus(
    visiblePlans: VisiblePlanLayout[],
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): Promise<PlanAndStatus[]> {
    const planIds = await getTilesPlanIds(visiblePlans, mapTiles, changeTimes);
    const withStatuses = await getTrackLayoutPlans(planIds, changeTimes.geometryPlan, true)
        .then((plans) =>
            plans
                .map((p) => p.layout)
                .filter(filterNotEmpty)
                .filter((p) => !p.planHidden),
        )
        .then((plans) =>
            getPlanLinkStatuses(
                plans.map((p) => p.id),
                layoutContext,
            ).then((statuses) =>
                plans.map((plan) => ({
                    plan,
                    status: statuses.find((s) => s.id === plan.id),
                })),
            ),
        );
    return withStatuses.filter(filterNotEmpty);
}

export async function getVisiblePlans(
    visiblePlans: VisiblePlanLayout[],
    mapTiles: MapTile[],
    changeTimes: ChangeTimes,
): Promise<GeometryPlanLayout[]> {
    const planIds = await getTilesPlanIds(visiblePlans, mapTiles, changeTimes);
    return (await getTrackLayoutPlans(planIds, changeTimes.geometryPlan, true))
        .map((r) => r.layout)
        .filter(filterNotEmpty)
        .filter((plan) => !plan.planHidden);
}

export async function getPlanAndStatus(
    plan: GeometryPlanLayout | undefined,
    layoutContext: LayoutContext,
): Promise<PlanAndStatus | undefined> {
    if (!plan) return undefined;
    else if (plan.planDataType === 'TEMP') return { plan, status: undefined };
    else return getPlanLinkStatus(plan.id, layoutContext).then((status) => ({ plan, status }));
}
