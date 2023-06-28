import Feature from 'ol/Feature';
import { Coordinate } from 'ol/coordinate';
import { Geometry, LineString, Point as OlPoint, Polygon } from 'ol/geom';
import { LAYOUT_SRID } from 'track-layout/track-layout-model';
import * as turf from '@turf/turf';
import { OptionalItemCollections } from 'selection/selection-model';
import { LayerItemSearchResult, SearchItemsOptions } from 'map/layers/utils/layer-model';
import proj4 from 'proj4';
import { Point } from 'model/geometry';
import { register } from 'ol/proj/proj4';
import VectorSource from 'ol/source/Vector';
import { filterNotEmpty } from 'utils/array-utils';

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

export function center(polygon: Polygon): OlPoint {
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

export function findMatchingEntities<T>(
    hitArea: Polygon,
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
    hitArea: Polygon,
    source: VectorSource,
): Feature<T>[] {
    const features: Feature<T>[] = [];

    source.forEachFeatureIntersectingExtent(hitArea.getExtent(), (f: Feature<T>) => {
        features.push(f);
    });

    return sortFeaturesByDistance(features, center(hitArea));
}

export function sortFeaturesByDistance<T extends Geometry>(features: Feature<T>[], point: OlPoint) {
    return features.sort((featureA, featureB) => {
        const geometryA = featureA.getGeometry();
        const geometryB = featureB.getGeometry();

        if (geometryA && geometryB) {
            return getDistance(point, geometryA) - getDistance(point, geometryB);
        } else if (geometryA) {
            return -1;
        } else {
            return 1;
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
