import { asyncCache } from 'cache/cache';
import { MapTile } from 'map/map-model';
import { AlignmentId, LocationTrackId, MapAlignment, MapAlignmentType } from './track-layout-model';
import { API_URI, getThrowError, getWithDefault, queryParams } from 'api/api-fetch';
import { BoundingBox, combineBoundingBoxes, Point } from 'model/geometry';
import { MAP_RESOLUTION_MULTIPLIER } from 'map/layers/layer-visibility-limits';
import { getChangeTimes } from 'common/change-time-api';
import { PublishType, TimeStamp, TrackMeter } from 'common/common-model';
import { LinkPoint } from 'linking/linking-model';
import { bboxString, pointString } from 'common/common-api';
import { getGeometryAlignmentLayout } from 'geometry/geometry-api';
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';
import { createLinkPoints } from 'linking/linking-store';

const locationTrackEndsCache = asyncCache<string, MapAlignment>();
const referenceLineEndsCache = asyncCache<string, MapAlignment>();
const alignmentTilesCache = asyncCache<string, MapAlignment[]>();

export const GEOCODING_URI = `${API_URI}/geocoding`;

type MapDataType = 'alignments' | 'location-tracks' | 'reference-lines';
export type AlignmentFetchType = 'locationtrack' | 'reference' | 'all';

function mapUri(dataType: MapDataType, publishType: PublishType, id?: string): string {
    const baseUri = `${TRACK_LAYOUT_URI}/map/${publishType.toLowerCase()}/${dataType}`;
    return id ? `${baseUri}/${id}` : baseUri;
}

function geocodingUri(publishType: PublishType) {
    return `${GEOCODING_URI}/${publishType.toLowerCase()}`;
}

export async function getAlignmentsByTile(
    changeTime: TimeStamp,
    mapTile: MapTile,
    publishType: PublishType,
    fetchType: AlignmentFetchType,
    selectedId?: LocationTrackId,
): Promise<MapAlignment[]> {
    const tileKey = `${mapTile.id}_${publishType}_${fetchType}`;
    return alignmentTilesCache.get(changeTime, tileKey, () =>
        getAlignments(mapTile.area, mapTile.resolution, publishType, fetchType, selectedId),
    );
}

export async function getAlignmentsByTiles(
    changeTime: TimeStamp,
    mapTiles: MapTile[],
    publishType: PublishType,
    fetchType: AlignmentFetchType,
    selectedId?: AlignmentId,
): Promise<MapAlignment[]> {
    return (
        await Promise.all(
            mapTiles.map((tile) =>
                getAlignmentsByTile(changeTime, tile, publishType, fetchType, selectedId),
            ),
        )
    ).flat();
}

export async function getReferenceLineSegmentEnds(
    id: LocationTrackId,
    publishType: PublishType,
): Promise<MapAlignment> {
    const cacheKey = `${id}_${publishType}`;
    return referenceLineEndsCache.get(getChangeTimes().layoutReferenceLine, cacheKey, () =>
        getThrowError<MapAlignment>(`${mapUri('reference-lines', publishType)}/${id}`),
    );
}

export async function getLocationTrackSegmentEnds(
    id: LocationTrackId,
    publishType: PublishType,
): Promise<MapAlignment> {
    const cacheKey = `${id}_${publishType}`;
    return locationTrackEndsCache.get(getChangeTimes().layoutLocationTrack, cacheKey, () =>
        getThrowError<MapAlignment>(`${mapUri('location-tracks', publishType)}/${id}`),
    );
}

export async function getLinkPointsByTiles(
    changeTime: TimeStamp,
    mapTiles: MapTile[],
    alignmentId: AlignmentId,
    alignmentType: MapAlignmentType,
): Promise<LinkPoint[]> {
    return (
        await Promise.all(
            mapTiles.map((tile) => getAlignmentsByTile(changeTime, tile, 'DRAFT', 'all')),
        ).then((alignments) => {
            const allAlignments = alignments.flat().filter((a) => a.alignmentType == alignmentType);
            if (allAlignments.length == 0) return [];
            const segments = allAlignments
                .filter((a) => a.id === alignmentId)
                .flatMap((a) => a.segments)
                .sort((a, b) => a.startM - b.startM);

            const uniqueIds = segments.map((s) => s.id);
            const uniqueSegments = segments.filter(
                ({ id }, index) => !uniqueIds.includes(id, index + 1),
            );

            console.log(uniqueSegments.map(s => `${s.startM}-${s.endM}`))
            return createLinkPoints(alignmentType, alignmentId, uniqueSegments);
        })
    ).flat();
}

export async function createGeometryLinkPointsByTiles(
    geometryPlanId: GeometryPlanId,
    geometryAlignmentId: GeometryAlignmentId,
    mapTiles: MapTile[],
    alwaysIncludePoints: LinkPoint[] = [],
): Promise<LinkPoint[]> {
    const resolution = toMapAlignmentResolution(mapTiles[0].resolution);
    const bounds = combineBoundingBoxes(mapTiles.map((tile) => tile.area));
    const geometryAlignment = await getGeometryAlignmentLayout(geometryPlanId, geometryAlignmentId);
    if (geometryAlignment) {
        return createLinkPoints(
            geometryAlignment.alignmentType,
            geometryAlignment.id,
            geometryAlignment.segments,
            bounds,
            (linkPoint: LinkPoint, isSegmentEndPoint: boolean) =>
                isSegmentEndPoint ||
                resolution <= 1 ||
                Math.floor(linkPoint.m) % resolution == 0 ||
                alwaysIncludePoints.some(
                    (alwaysIncludePoint) => alwaysIncludePoint.id == linkPoint.id,
                ),
        );
    } else {
        return [];
    }
}

async function getAlignments(
    area: BoundingBox,
    resolution: number,
    publishType: PublishType,
    fetchType: AlignmentFetchType,
    selectedId?: LocationTrackId,
): Promise<MapAlignment[]> {
    const params = queryParams({
        resolution: toMapAlignmentResolution(resolution),
        bbox: bboxString(area),
        type: fetchType.toUpperCase(),
        selectedId,
    });
    return await getWithDefault<MapAlignment[]>(
        `${mapUri('alignments', publishType)}${params}`,
        [],
    );
}

export function toMapAlignmentResolution(tileResolution: number): number {
    return parseFloat(Math.ceil(tileResolution * MAP_RESOLUTION_MULTIPLIER).toPrecision(1));
}

export async function getTrackMeter(
    trackNumberId: string,
    publishType: PublishType,
    location: Point,
): Promise<TrackMeter | undefined> {
    const params = queryParams({ coordinate: pointString(location) });

    return getWithDefault(
        `${geocodingUri(publishType)}/address/${trackNumberId}${params}`,
        undefined,
    );
}
