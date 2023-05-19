import Feature, { FeatureLike } from 'ol/Feature';
import { Coordinate } from 'ol/coordinate';
import { Geometry, LineString, Point as OlPoint, Polygon } from 'ol/geom';
import { RegularShape, Style } from 'ol/style';
import {
    LAYOUT_SRID,
    LayoutKmPost,
    LayoutPoint,
    LayoutSwitch,
    LayoutSwitchJoint,
} from 'track-layout/track-layout-model';
import * as turf from '@turf/turf';
import { OptionalItemCollections } from 'selection/selection-model';
import { FEATURE_PROPERTY_LINK_POINT, FEATURE_PROPERTY_TYPE } from 'map/layers/linking-layer';
import { LayerItemSearchResult } from 'map/layers/utils/layer-model';
import { LinkPoint, LinkPointType, SuggestedSwitch } from 'linking/linking-model';
import proj4 from 'proj4';
import { FEATURE_PROPERTY_SUGGESTED_SWITCH } from 'map/layers/switch/switch-linking-layer';
import { GeometryPlanId } from 'geometry/geometry-model';
import { OptionalShownItems } from 'map/map-model';
import { Extent, getBottomRight, getTopLeft } from 'ol/extent';
import {
    BoundingBox,
    boundingBoxAroundPoints,
    boundingBoxIntersectsLine,
    coordsToPoint,
    createLine,
    Point,
} from 'model/geometry';
import { AlignmentDataHolder, AlignmentHeader } from 'track-layout/layout-map-api';
import { interpolateXY } from 'utils/math-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { register } from 'ol/proj/proj4';
import VectorSource from 'ol/source/Vector';

proj4.defs(LAYOUT_SRID, '+proj=utm +zone=35 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs');
register(proj4);

const layoutToWgs84 = proj4(LAYOUT_SRID, 'WGS84');

function toWgs84(coordinate: number[]): number[] {
    return layoutToWgs84.forward(coordinate);
}

function toMapProjection(coordinate: number[]): number[] {
    return layoutToWgs84.inverse(coordinate);
}

function toWgs84Multi(coordinates: number[][]): number[][] {
    return coordinates.map((coord) => toWgs84(coord));
}

function toWgs84Polygon(polyCoordinates: number[][][]): number[][][] {
    return polyCoordinates.map((coordinates) => toWgs84Multi(coordinates));
}

function center(polygon: Polygon): OlPoint {
    const coords = polygon.getLinearRing(0)?.getCoordinates();
    if (!coords) {
        throw 'Cannot find the center of a polygon!';
    }

    const center = turf.center(turf.points(toWgs84Multi(coords))).geometry.coordinates;
    if (!center) {
        throw 'Cannot find the center of a polygon!';
    }
    return new OlPoint(toMapProjection(center));
}

function hasCollisionPoints(pointA: OlPoint, pointB: OlPoint): boolean {
    return pointA.intersectsCoordinate(pointB.getCoordinates());
}

function hasCollisionPolygonAndPoint(poly: Polygon, point: OlPoint): boolean {
    return turf.booleanPointInPolygon(
        turf.point(toWgs84(point.getCoordinates())),
        turf.polygon(toWgs84Polygon(poly.getCoordinates())),
    );
}

function extentToBoundingBox(extent: Extent): BoundingBox {
    const topLeft = getTopLeft(extent);
    const bottomRight = getBottomRight(extent);
    return boundingBoxAroundPoints([
        { x: topLeft[0], y: topLeft[1] },
        { x: bottomRight[0], y: bottomRight[1] },
    ]);
}

function hasCollisionRectangleAndLine(rect: Extent, line: LineString): boolean {
    const bbox = extentToBoundingBox(rect);
    const coords = line.getCoordinates();
    for (let i = 0; i < coords.length - 1; i++) {
        const line = createLine(coordsToPoint(coords[i]), coordsToPoint(coords[i + 1]));
        if (boundingBoxIntersectsLine(bbox, line)) {
            return true;
        }
    }
    return false;
}

function hasCollisionPolygonAndLine(poly: Polygon, line: LineString): boolean {
    // Simplify collision detection to rectangle version for now
    return hasCollisionRectangleAndLine(poly.getExtent(), line);
}

function hasCollisionPolygons(polyA: Polygon, polyB: Polygon): boolean {
    const turfPolyA = turf.polygon(toWgs84Polygon(polyA.getCoordinates()));
    const turfPolyB = turf.polygon(toWgs84Polygon(polyB.getCoordinates()));
    return !turf.booleanDisjoint(turfPolyA, turfPolyB);
}

/**
 * Returns true if given geometries collides.
 */
function hasCollision(geomA: Geometry, geomB: Geometry): boolean {
    if (geomA instanceof OlPoint && geomB instanceof OlPoint) {
        return hasCollisionPoints(geomA, geomB);
    } else if (geomA instanceof Polygon && geomB instanceof OlPoint) {
        return hasCollisionPolygonAndPoint(geomA, geomB);
    } else if (geomA instanceof OlPoint && geomB instanceof Polygon) {
        return hasCollisionPolygonAndPoint(geomB, geomA);
    } else if (geomA instanceof Polygon && geomB instanceof LineString) {
        return hasCollisionPolygonAndLine(geomA, geomB);
    } else if (geomA instanceof LineString && geomB instanceof Polygon) {
        return hasCollisionPolygonAndLine(geomB, geomA);
    } else if (geomA instanceof Polygon && geomB instanceof Polygon) {
        return hasCollisionPolygons(geomA, geomB);
    }
    throw `Geometry combination ${geomA} and ${geomB} is not supported`;
}

/**
 * Return a distance between two point in meters
 *
 * @param pointA
 * @param pointB
 */
function getPlanarDistancePointAndPoint(pointA: OlPoint, pointB: OlPoint): number {
    const pointACoords = pointA.getCoordinates();
    const pointBCoords = pointB.getCoordinates();

    return Math.hypot(pointACoords[0] - pointBCoords[0], pointACoords[1] - pointBCoords[1]);
}

export function getPlanarDistanceUnwrapped(x1: number, y1: number, x2: number, y2: number): number {
    return Math.hypot(x1 - x2, y1 - y2);
}

/**
 * Return the shortest distance between the point and the line in meters
 *
 * @param point
 * @param line
 */
export function getDistancePointAndLine(point: OlPoint, line: LineString): number {
    return (
        turf.pointToLineDistance(
            turf.point(toWgs84(point.getCoordinates())),
            turf.lineString(toWgs84Multi(line.getCoordinates())),
            { units: 'kilometers' },
        ) * 1000
    );
}

export function getDistancePointAndPolygon(point: OlPoint, polygon: Polygon): number {
    const polyCenter = center(polygon);
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

export type MatchOptions = {
    strategy?: 'limit' | 'nearest';
    limit?: number;
};

type Match<TEntity> = {
    feature: Feature<Geometry>;
    entity: TEntity;
};

type SortedMatch<TEntity> = Match<TEntity> & {
    distance: number;
};

function sortMatchesByDistance<TEntity>(
    point: OlPoint,
    matches: Match<TEntity>[],
): SortedMatch<TEntity>[] {
    return Object.values(matches)
        .map((match) => {
            const featGeom = match.feature.getGeometry();
            if (!featGeom) {
                return undefined;
            }
            return {
                distance: featGeom && getDistance(point, featGeom),
                ...match,
            };
        })
        .filter(filterNotEmpty) // remove undefined items
        .sort((a, b) => (a.distance < b.distance ? -1 : 1));
}

function hasAccurateMatch(shape: Polygon, feature: Feature<Geometry>): boolean {
    const geom = feature.getGeometry();
    return geom !== undefined && hasCollision(shape, geom);
}

function findEntities<TVal>(
    shape: Polygon,
    features: Feature<Geometry>[],
    getEntity: (feature: Feature<Geometry>) => [string, TVal] | undefined,
    options?: MatchOptions,
): TVal[] {
    const match: { [key: string]: { feature: Feature<Geometry>; entity: TVal } } = {};
    let itemCount = 0;
    // Use "some" instead of "forEach" to stop iteration when needed (e.g. enough hits)
    features.some((feature) => {
        let continueSearching = true;

        const entityInfo = getEntity(feature);
        if (entityInfo) {
            const [id, entity] = entityInfo;
            if (entity && !match[id] && hasAccurateMatch(shape, feature)) {
                // New match found
                match[id] = {
                    feature: feature,
                    entity: entity,
                };
                itemCount++;

                if (options?.strategy == 'limit' && options?.limit && itemCount >= options?.limit) {
                    // Limit exceeded
                    continueSearching = false;
                }
            }
        }
        return !continueSearching; // return false to continue, true to stop
    });

    if (options?.strategy == 'nearest') {
        const matches = Object.values(match);
        return sortMatchesByDistance(center(shape), matches)
            .slice(0, options?.limit || matches.length)
            .map((sortedMatch) => sortedMatch.entity);
    }

    return Object.values(match).map((match) => match.entity);
}

// Don't export this: use getAlignmentData / setAlignmentData instead for type safety
const FEATURE_PROPERTY_ALIGNMENT_DATA = 'segment-data';

export function setAlignmentData(feature: Feature<LineString>, dataHolder: AlignmentDataHolder) {
    feature.set(FEATURE_PROPERTY_ALIGNMENT_DATA, dataHolder);
}

export function getAlignmentData(feature: FeatureLike): AlignmentDataHolder {
    return feature.get(FEATURE_PROPERTY_ALIGNMENT_DATA) as AlignmentDataHolder;
}

export function getMatchingAlignmentData(
    shape: Polygon,
    features: Feature<Geometry>[],
    options?: MatchOptions,
): AlignmentDataHolder[] {
    return findEntities(
        shape,
        features,
        (feature) => {
            const data = getAlignmentData(feature);
            return data ? [alignmentId(data.header), data] : undefined;
        },
        options,
    );
}

// Match by type+id so that tracks and reference lines won't get mixed
export function alignmentId(header: AlignmentHeader): string {
    const typeId = header.alignmentType === 'LOCATION_TRACK' ? 'LT_' : 'RL_';
    return `${typeId}${header.id}`;
}

export function getMatchingLinkPoints(
    shape: Polygon,
    type: LinkPointType,
    features: Feature<Geometry>[],
    options?: MatchOptions,
): LinkPoint[] {
    return findEntities(
        shape,
        features,
        (feature) => {
            const linkPoint = feature.get(FEATURE_PROPERTY_LINK_POINT) as LinkPoint;
            return linkPoint && feature.get(FEATURE_PROPERTY_TYPE) == type
                ? [linkPoint.id, linkPoint]
                : undefined;
        },
        options,
    );
}

export type KmPostDataHolder = {
    kmPost: LayoutKmPost;
    planId?: GeometryPlanId;
};

export function getMatchingKmPosts(
    shape: Polygon,
    features: Feature<Geometry>[],
    options?: MatchOptions,
): KmPostDataHolder[] {
    return findEntities(
        shape,
        features,
        (feature) => {
            const data = feature.get('kmPost-data') as KmPostDataHolder;
            return data ? [data.kmPost.id, data] : undefined;
        },
        options,
    );
}

export type SwitchDataHolder = {
    switch: LayoutSwitch;
    joint?: LayoutSwitchJoint;
    planId?: GeometryPlanId;
};

export function getMatchingSwitches(
    shape: Polygon,
    features: Feature<Geometry>[],
    options?: MatchOptions,
): SwitchDataHolder[] {
    return findEntities(
        shape,
        features,
        (feature) => {
            const data = feature.get('switch-data') as SwitchDataHolder;
            return data ? [data.switch.id, data] : undefined;
        },
        options,
    );
}

export function getMatchingSuggestedSwitches(
    shape: Polygon,
    features: Feature<Geometry>[],
    options?: MatchOptions,
): SuggestedSwitch[] {
    return findEntities(
        shape,
        features,
        (feature) => {
            const suggestedSwitch = feature.get(
                FEATURE_PROPERTY_SUGGESTED_SWITCH,
            ) as SuggestedSwitch;
            return suggestedSwitch ? [suggestedSwitch.id, suggestedSwitch] : undefined;
        },
        options,
    );
}

export function getMatchingEntities<T extends { id: string }>(
    shape: Polygon,
    features: Feature<Geometry>[],
    propertyName: string,
    options?: MatchOptions,
): T[] {
    return findEntities(
        shape,
        features,
        (feature) => {
            const entity = feature.get(propertyName) as T;
            return entity ? [entity.id || '', entity] : undefined;
        },
        options,
    );
}

export const clearFeatures = (vectorSource: VectorSource) => vectorSource.clear();

export function getTickStyle(
    point1: Coordinate,
    point2: Coordinate,
    length: number,
    position: 'start' | 'end',
    style: Style,
): Style {
    const angleVersionCount = 100;
    const angleStep = (Math.PI * 2) / angleVersionCount;
    const actualAngle = Math.atan2(point1[0] - point2[0], point1[1] - point2[1]) + Math.PI / 2;
    const roundAngle = Math.round(actualAngle / angleStep) * angleStep;
    const image = new RegularShape({
        stroke: style.getStroke(),
        points: 2,
        radius: length,
        radius2: 0,
        angle: roundAngle,
    });

    return new Style({
        geometry: new OlPoint(position == 'start' ? point1 : point2),
        image: image,
        zIndex: style.getZIndex(),
    });
}

export function getTickStyles(
    points: LayoutPoint[],
    mValues: number[],
    length: number,
    style: Style,
): Style[] {
    if (points.length < 2) {
        return [];
    }
    return mValues
        .map((m) => {
            const coordinate = getCoordinate(points, m);
            if (!coordinate) {
                return undefined;
            } else if (m >= points[points.length - 1].m) {
                const prev = points[points.length - 2];
                return getTickStyle(pointToCoords(prev), coordinate, length, 'end', style);
            } else {
                const next = points.find((p) => p.m > m);
                return next
                    ? getTickStyle(coordinate, pointToCoords(next), length, 'start', style)
                    : undefined;
            }
        })
        .filter(filterNotEmpty);
}

function getCoordinate(points: LayoutPoint[], m: number): number[] | undefined {
    const nextIndex = points.findIndex((p) => p.m >= m);
    if (nextIndex < 0 || nextIndex >= points.length) {
        return undefined;
    } else if (points[nextIndex].m === m) {
        return pointToCoords(points[nextIndex]);
    } else if (nextIndex === 0) {
        return undefined;
    } else {
        return interpolateXY(points[nextIndex - 1], points[nextIndex], m);
    }
}

function mergeOptionalArrays<T>(a1: T[] | undefined, a2: T[] | undefined): T[] | undefined {
    if (a1 === undefined) return a2;
    if (a2 === undefined) return a1;
    else return [...a1, ...a2];
}

export function mergePartialItemSearchResults(
    ...searchResults: OptionalItemCollections[]
): LayerItemSearchResult {
    return searchResults.reduce<OptionalItemCollections>((merged, searchResult) => {
        return {
            locationTracks: mergeOptionalArrays(merged.locationTracks, searchResult.locationTracks),
            kmPosts: mergeOptionalArrays(merged.kmPosts, searchResult.kmPosts),
            geometryKmPosts: mergeOptionalArrays(
                merged.geometryKmPosts,
                searchResult.geometryKmPosts,
            ),
            switches: mergeOptionalArrays(merged.switches, searchResult.switches),
            geometrySwitches: mergeOptionalArrays(
                merged.geometrySwitches,
                searchResult.geometrySwitches,
            ),
            trackNumbers: mergeOptionalArrays(merged.trackNumbers, searchResult.trackNumbers),
            geometryAlignments: mergeOptionalArrays(
                merged.geometryAlignments,
                searchResult.geometryAlignments,
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
            suggestedSwitches: mergeOptionalArrays(
                merged.suggestedSwitches,
                searchResult.suggestedSwitches,
            ),
            locationTrackEndPoints: mergeOptionalArrays(
                merged.locationTrackEndPoints,
                searchResult.locationTrackEndPoints,
            ),
            geometryPlans: mergeOptionalArrays(merged.geometryPlans, searchResult.geometryPlans),
        };
    }, {});
}

export function pointToCoords(point: Point): Coordinate {
    return [point.x, point.y];
}

export function mergePartialShownItemSearchResults(
    ...searchResults: OptionalShownItems[]
): OptionalShownItems {
    return searchResults.reduce<OptionalShownItems>((merged, searchResult) => {
        return {
            locationTracks: mergeOptionalArrays(merged.locationTracks, searchResult.locationTracks),
            referenceLines: mergeOptionalArrays(merged.referenceLines, searchResult.referenceLines),
            kmPosts: mergeOptionalArrays(merged.kmPosts, searchResult.kmPosts),
            switches: mergeOptionalArrays(merged.switches, searchResult.switches),
        };
    }, {});
}
