import { Result } from 'neverthrow';
import { asyncCache } from 'cache/cache';
import { MapTile } from 'map/map-model';
import {
    AlignmentId,
    AlignmentStartAndEnd,
    LayoutKmPost,
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutLocationTrackDuplicate,
    LayoutPoint,
    LayoutReferenceLine,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutSwitchJointConnection,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    MapAlignment,
    MapAlignmentType,
    MapSegment,
    ReferenceLineId,
} from './track-layout-model';
import {
    API_URI,
    deleteAdt,
    getIgnoreError,
    getThrowError,
    getWithDefault,
    postIgnoreError,
    putIgnoreError,
    queryParams,
} from 'api/api-fetch';
import { BoundingBox, boundingBoxContains, combineBoundingBoxes, Point } from 'model/geometry';
import { MAP_RESOLUTION_MULTIPLIER } from 'map/layers/layer-visibility-limits';
import {
    getChangeTimes,
    updateReferenceLineChangeTime,
    updateTrackNumberChangeTime,
} from 'common/change-time-api';
import { ChangeTimes, KmNumber, PublishType, TimeStamp, TrackMeter } from 'common/common-model';
import { LinkInterval, LinkPoint, LocationTrackSaveError } from 'linking/linking-model';
import { bboxString, pointString } from 'common/common-api';
import { getGeometryAlignmentLayout } from 'geometry/geometry-api';
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { filterNotEmpty } from 'utils/array-utils';
import { TrackNumberSaveRequest } from 'tool-panel/track-number/dialog/track-number-edit-store';
import { directionBetweenPoints } from 'utils/math-utils';

const trackNumbersCache = asyncCache<string, LayoutTrackNumber[]>();
const locationTrackEndsCache = asyncCache<string, MapAlignment>();
const referenceLineEndsCache = asyncCache<string, MapAlignment>();
const referenceLineCache = asyncCache<string, LayoutReferenceLine | null>();
const locationTrackCache = asyncCache<string, LayoutLocationTrack | null>();
const alignmentTilesCache = asyncCache<string, MapAlignment[]>();
const switchGroupsCache = asyncCache<string, LayoutSwitch[]>();
const switchCache = asyncCache<string, LayoutSwitch>();
const kmPostListCache = asyncCache<string, LayoutKmPost[]>();
const kmPostForLinkingCache = asyncCache<string, LayoutKmPost[]>();
const kmPostCache = asyncCache<string, LayoutKmPost | null>();

export const TRACK_LAYOUT_URI = `${API_URI}/track-layout`;
export const GEOCODING_URI = `${API_URI}/geocoding`;

type LayoutDataType =
    | 'track-numbers'
    | 'km-posts'
    | 'switches'
    | 'location-tracks'
    | 'reference-lines';

function layoutUri(dataType: LayoutDataType, publishType: PublishType, id?: string) {
    const baseUri = `${TRACK_LAYOUT_URI}/${dataType}/${publishType.toLowerCase()}`;
    return id ? `${baseUri}/${id}` : baseUri;
}

function geocodingUri(publishType: PublishType) {
    return `${GEOCODING_URI}/${publishType.toLowerCase()}`;
}

export async function getAlignmentsByTile(
    changeTime: TimeStamp,
    mapTile: MapTile,
    publishType: PublishType,
    referenceLineOnly: boolean,
    selectedId?: LocationTrackId,
): Promise<MapAlignment[]> {
    const tileKey = `${mapTile.id}_${publishType}_${referenceLineOnly ? '1' : '0'}`;
    return alignmentTilesCache.get(changeTime, tileKey, () =>
        getAlignments(mapTile.area, mapTile.resolution, publishType, referenceLineOnly, selectedId),
    );
}

export async function getSwitchesByTile(
    changeTime: TimeStamp,
    mapTile: MapTile,
    publishType: PublishType,
): Promise<LayoutSwitch[]> {
    const tileKey = `${mapTile.id}_${publishType}`;
    return switchGroupsCache.get(changeTime, tileKey, () =>
        getSwitchesByBoundingBox(mapTile.area, publishType),
    );
}

export async function getAlignmentsByTiles(
    changeTime: TimeStamp,
    mapTiles: MapTile[],
    publishType: PublishType,
    referenceLineOnly: boolean,
    selectedId?: AlignmentId,
): Promise<MapAlignment[]> {
    return (
        await Promise.all(
            mapTiles.map((tile) =>
                getAlignmentsByTile(changeTime, tile, publishType, referenceLineOnly, selectedId),
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
        getThrowError<MapAlignment>(`${layoutUri(publishType)}/map/reference-lines/${id}`),
    );
}

export async function getLocationTrackSegmentEnds(
    id: LocationTrackId,
    publishType: PublishType,
): Promise<MapAlignment> {
    const cacheKey = `${id}_${publishType}`;
    return locationTrackEndsCache.get(getChangeTimes().layoutLocationTrack, cacheKey, () =>
        getThrowError<MapAlignment>(`${layoutUri(publishType)}/map/location-tracks/${id}`),
    );
}

export async function getReferenceLineStartAndEnd(
    referenceLineId: ReferenceLineId,
    publishType: PublishType,
): Promise<AlignmentStartAndEnd | undefined> {
    return getThrowError<AlignmentStartAndEnd>(
        `${layoutUri('reference-lines', publishType, referenceLineId)}/start-and-end`,
    );
}

export async function getLocationTrackStartAndEnd(
    locationTrackId: LocationTrackId,
    publishType: PublishType,
): Promise<AlignmentStartAndEnd | undefined> {
    return getThrowError<AlignmentStartAndEnd>(
        `${layoutUri('location-tracks', publishType, locationTrackId)}/start-and-end`,
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
            mapTiles.map((tile) => getAlignmentsByTile(changeTime, tile, 'DRAFT', false)),
        ).then((alignments) => {
            const allAlignments = alignments.flat().filter((a) => a.alignmentType == alignmentType);
            if (allAlignments.length == 0) return [];
            const segments = allAlignments
                .filter((a) => a.id === alignmentId)
                .flatMap((a) => a.segments)
                .sort((a, b) => a.start - b.start);

            const uniqueIds = segments.map((s) => s.id);
            const uniqueSegments = segments.filter(
                ({ id }, index) => !uniqueIds.includes(id, index + 1),
            );

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
                Math.floor(linkPoint.ordering) % resolution == 0 ||
                alwaysIncludePoints.some(
                    (alwaysIncludePoint) => alwaysIncludePoint.id == linkPoint.id,
                ),
        );
    } else {
        return [];
    }
}

export function createEndLinkPoints(
    alignmentType: MapAlignmentType,
    alignmentId: AlignmentId,
    segments: MapSegment[],
): LinkInterval {
    if (segments.length == 0) {
        return {
            start: undefined,
            end: undefined,
        };
    }
    const firstSegment = segments[0];
    const lastSegment = segments[segments.length - 1];
    return {
        start: createLinkPoint(
            alignmentType,
            alignmentId,
            firstSegment,
            0,
            firstSegment.points[0],
            directionBetweenPoints(firstSegment.points[0], firstSegment.points[1]),
            true,
        ),
        end: createLinkPoint(
            alignmentType,
            alignmentId,
            lastSegment,
            // use full segment point-count as the points might be simplified
            lastSegment.pointCount - 1,
            lastSegment.points[lastSegment.points.length - 1],
            directionBetweenPoints(
                lastSegment.points[lastSegment.points.length - 2],
                lastSegment.points[lastSegment.points.length - 1],
            ),
            true,
        ),
    };
}

function createLinkPoints(
    alignmentType: MapAlignmentType,
    alignmentId: AlignmentId,
    segments: MapSegment[],
    bounds?: BoundingBox,
    filter: ((linkPoint: LinkPoint, isSegmentEndPoint: boolean) => boolean) | undefined = undefined,
): LinkPoint[] {
    return segments.flatMap((segment, sIdx) => {
        const isFirstSegment = sIdx === 0;
        const isLastSegment = sIdx === segments.length - 1;

        return segment.points
            .flatMap((point, pIdx) => {
                const isFirstPoint = pIdx === 0;
                const isLastPoint = pIdx === segment.points.length - 1;
                const isSegmentEndPoint = isFirstPoint || isLastPoint;

                if (!isLastSegment && isLastPoint) return null;
                if (bounds != undefined && !boundingBoxContains(bounds, point)) return null;

                const direction = isLastPoint
                    ? directionBetweenPoints(segment.points[pIdx - 1], point)
                    : directionBetweenPoints(point, segment.points[pIdx + 1]);

                const linkPoint = createLinkPoint(
                    alignmentType,
                    alignmentId,
                    segment,
                    pIdx,
                    point,
                    direction,
                    (isFirstSegment && isFirstPoint) || (isLastSegment && isLastPoint),
                );

                if (filter && !filter(linkPoint, isSegmentEndPoint)) return null;
                return linkPoint;
            })
            .filter(filterNotEmpty);
    });
}

function createLinkPoint(
    alignmentType: MapAlignmentType,
    alignmentId: AlignmentId,
    segment: MapSegment,
    pointIndex: number,
    point: LayoutPoint,
    direction: number | undefined,
    isEndPoint: boolean,
): LinkPoint {
    return {
        id: `${segment.id}_${pointIndex}`,
        alignmentType: alignmentType,
        alignmentId: alignmentId,
        segmentId: segment.id,
        ordering: segment.start + point.m,
        x: point.x,
        y: point.y,
        isSegmentEndPoint: pointIndex === 0 || pointIndex == segment.points.length - 1,
        isEndPoint: isEndPoint,
        direction: direction,
    };
}

async function getAlignments(
    area: BoundingBox,
    resolution: number,
    publishType: PublishType,
    referenceLineOnly: boolean,
    selectedId?: LocationTrackId,
): Promise<MapAlignment[]> {
    const params = queryParams({
        resolution: toMapAlignmentResolution(resolution),
        bbox: bboxString(area),
        type: referenceLineOnly ? 'REFERENCE' : null,
        selectedId,
    });
    return await getWithDefault<MapAlignment[]>(
        `${layoutUri(publishType)}/map/alignments${params}`,
        [],
    );
}

export function toMapAlignmentResolution(tileResolution: number): number {
    return parseFloat(Math.ceil(tileResolution * MAP_RESOLUTION_MULTIPLIER).toPrecision(1));
}

export async function getLocationTracksBySearchTerm(
    searchTerm: string,
    publishType: PublishType,
    limit: number,
): Promise<LayoutLocationTrack[]> {
    const params = queryParams({
        searchTerm: searchTerm,
        limit: limit,
    });
    return await getWithDefault<LayoutLocationTrack[]>(
        `${layoutUri('location-tracks', publishType)}${params}`,
        [],
    );
}

export const getReferenceLineChangeTimes = (id: ReferenceLineId): Promise<ChangeTimes> => {
    return getThrowError<ChangeTimes>(
        `${layoutUri('reference-lines', 'OFFICIAL', id)}/change-times`,
    );
};

export const getLocationTrackChangeTimes = (id: LocationTrackId): Promise<ChangeTimes> => {
    return getThrowError<ChangeTimes>(
        `${layoutUri('location-tracks', 'OFFICIAL', id)}/change-times`,
    );
};

export async function getSwitchesByBoundingBox(
    bbox: BoundingBox,
    publishType: PublishType,
    _searchTerm?: string,
    comparisonPoint?: Point,
    includeSwitchesWithNoJoints = false,
): Promise<LayoutSwitch[]> {
    const params = queryParams({
        bbox: bboxString(bbox),
        comparisonPoint: comparisonPoint && pointString(comparisonPoint),
        includeSwitchesWithNoJoints: includeSwitchesWithNoJoints,
    });
    return await getWithDefault<LayoutSwitch[]>(
        `${layoutUri('switches', publishType)}${params}`,
        [],
    );
}

export async function getSwitchesBySearchTerm(
    searchTerm: string,
    publishType: PublishType,
    limit: number,
): Promise<LayoutSwitch[]> {
    const params = queryParams({
        searchTerm: searchTerm,
        limit: limit,
    });
    return await getWithDefault<LayoutSwitch[]>(
        `${layoutUri('switches', publishType)}${params}`,
        [],
    );
}

export async function getSwitch(
    switchId: LayoutSwitchId,
    publishType: PublishType,
): Promise<LayoutSwitch> {
    const cacheKey = `${switchId}_${publishType}`;
    return switchCache.get(getChangeTimes().layoutSwitch, cacheKey, () =>
        getThrowError<LayoutSwitch>(layoutUri('switches', publishType, switchId)),
    );
}

// NOTE: There's no front-end caching for mass fetches yet, so it's preferable to use separate single fetches if you
// know that the data is most likely in cache. Caching will be implemented in GVT-1428
export async function getSwitches(
    switchIds: LayoutSwitchId[],
    publishType: PublishType,
): Promise<LayoutSwitch[]> {
    return switchIds.length > 0
        ? getThrowError<LayoutSwitch[]>(`${layoutUri('switches', publishType)}?ids=${switchIds}`)
        : Promise.resolve([]);
}

export async function getKmPost(
    id: LayoutKmPostId,
    publishType: PublishType,
    changeTime: TimeStamp = getChangeTimes().layoutKmPost,
): Promise<LayoutKmPost | null> {
    const cacheKey = `${id}_${publishType}`;
    return kmPostCache.get(changeTime, cacheKey, () =>
        getIgnoreError<LayoutKmPost>(layoutUri('km-posts', publishType, id)),
    );
}

export async function getKmPostByNumber(
    publishType: PublishType,
    trackNumberId: LayoutTrackNumberId,
    kmNumber: KmNumber,
): Promise<LayoutKmPost | null> {
    const params = queryParams({
        trackNumberId: trackNumberId,
        kmNumber: kmNumber,
    });
    return getIgnoreError<LayoutKmPost>(`${layoutUri('km-posts', publishType)}${params}`);
}

export async function getKmPostsByTile(
    publishType: PublishType,
    changeTime: TimeStamp,
    bbox: BoundingBox,
    step: number,
): Promise<LayoutKmPost[]> {
    const params = queryParams({
        bbox: bboxString(bbox),
        step: Math.ceil(step),
        publishType,
    });
    return kmPostListCache.get(changeTime, `${publishType}_${JSON.stringify(params)}`, () =>
        getThrowError(`${layoutUri('km-posts', publishType)}${params}`),
    );
}

export async function getKmPostForLinking(
    publishType: PublishType,
    trackNumberId: LayoutTrackNumberId,
    location: Point,
    offset = 0,
    limit = 20,
): Promise<LayoutKmPost[]> {
    const kmPostChangeTime = getChangeTimes().layoutKmPost;
    const params = queryParams({
        trackNumberId: trackNumberId,
        location: pointString(location),
        offset: offset,
        limit: limit,
    });
    return kmPostForLinkingCache.get(kmPostChangeTime, params, () =>
        getThrowError(`${layoutUri('km-posts', publishType)}${params}`),
    );
}

export async function getTrackNumberById(
    trackNumberId: LayoutTrackNumberId,
    publishType: PublishType,
    changeTime?: TimeStamp,
): Promise<LayoutTrackNumber | undefined> {
    return getTrackNumbers(publishType, changeTime).then((trackNumbers) =>
        trackNumbers.find((trackNumber) => trackNumber.id == trackNumberId),
    );
}

export async function getNonLinkedReferenceLines(): Promise<LayoutReferenceLine[]> {
    return getWithDefault<LayoutReferenceLine[]>(
        `${layoutUri('reference-lines', 'DRAFT')}/non-linked`,
        [],
    );
}

export async function getNonLinkedLocationTracks(): Promise<LayoutLocationTrack[]> {
    return getWithDefault<LayoutLocationTrack[]>(
        `${layoutUri('location-tracks', 'DRAFT')}/non-linked`,
        [],
    );
}

export async function getTrackNumberReferenceLine(
    trackNumberId: LayoutTrackNumberId,
    publishType: PublishType,
    changeTime?: TimeStamp,
): Promise<LayoutReferenceLine | null> {
    const cacheKey = `TN_${trackNumberId}_${publishType}`;
    return referenceLineCache.get(
        changeTime || getChangeTimes().layoutReferenceLine,
        cacheKey,
        () =>
            getIgnoreError<LayoutReferenceLine>(
                `${layoutUri('reference-lines', publishType)}/by-track-number/${trackNumberId}`,
            ),
    );
}

export async function getReferenceLine(
    id: ReferenceLineId,
    publishType: PublishType,
    changeTime?: TimeStamp,
): Promise<LayoutReferenceLine | null> {
    const cacheKey = `${id}_${publishType}`;
    return referenceLineCache.get(
        changeTime || getChangeTimes().layoutReferenceLine,
        cacheKey,
        () => getIgnoreError<LayoutReferenceLine>(layoutUri('reference-lines', publishType, id)),
    );
}

export async function getLocationTrack(
    id: LocationTrackId,
    publishType: PublishType,
    changeTime?: TimeStamp,
): Promise<LayoutLocationTrack | null> {
    const cacheKey = `${id}_${publishType}`;
    return locationTrackCache.get(
        changeTime || getChangeTimes().layoutLocationTrack,
        cacheKey,
        () => getIgnoreError<LayoutLocationTrack>(layoutUri('location-tracks', publishType, id)),
    );
}

// There's no front-end caching for mass fetches yet, so it's preferable to use separate single fetches if you
// know that the data is most likely in cache. Caching will be implemented in GVT-1428
export async function getLocationTracks(ids: LocationTrackId[], publishType: PublishType) {
    return ids.length > 0
        ? getThrowError<LayoutLocationTrack[]>(
              `${layoutUri('location-tracks', publishType)}?ids=${ids}`,
          )
        : Promise.resolve([]);
}

export async function getTrackAddress(
    trackNumberId: string,
    publishType: PublishType,
    coordinate: Point,
): Promise<TrackMeter | undefined> {
    const params = queryParams({ coordinate: pointString(coordinate) });
    return getWithDefault<TrackMeter | undefined>(
        `${geocodingUri(publishType)}/address/${trackNumberId}${params}`,
        undefined,
    );
}

export async function getLocationTrackDuplicates(
    publishType: PublishType,
    id: LocationTrackId,
): Promise<LayoutLocationTrackDuplicate[]> {
    return getThrowError<LayoutLocationTrackDuplicate[]>(
        `${layoutUri('location-tracks', publishType, 'id')}/duplicate-of`,
    );
}

export async function getReferenceLinesNear(
    publishType: PublishType,
    bbox: BoundingBox,
): Promise<LayoutReferenceLine[]> {
    const params = queryParams({ bbox: bboxString(bbox) });
    return getThrowError<LayoutReferenceLine[]>(
        `${layoutUri('reference-lines', publishType)}${params}`,
    );
}

export async function getLocationTracksNear(
    publishType: PublishType,
    bbox: BoundingBox,
): Promise<LayoutLocationTrack[]> {
    const params = queryParams({ bbox: bboxString(bbox) });
    return getThrowError<LayoutLocationTrack[]>(
        `${layoutUri('location-tracks', publishType)}${params}`,
    );
}

export async function getTrackNumbers(
    publishType: PublishType,
    changeTime?: TimeStamp,
): Promise<LayoutTrackNumber[]> {
    return trackNumbersCache.get(
        changeTime || getChangeTimes().layoutTrackNumber,
        publishType,
        () => getThrowError<LayoutTrackNumber[]>(layoutUri('track-numbers', publishType)),
    );
}

export async function updateTrackNumber(
    trackNumberId: LayoutTrackNumberId,
    request: TrackNumberSaveRequest,
): Promise<LayoutTrackNumberId | null> {
    const path = layoutUri('track-numbers', 'DRAFT', trackNumberId);
    return await putIgnoreError<TrackNumberSaveRequest, LayoutTrackNumberId>(path, request).then(
        (rs) => updateTrackNumberChangeTime().then((_) => rs),
    );
}

export async function createTrackNumber(
    request: TrackNumberSaveRequest,
): Promise<LayoutTrackNumberId | null> {
    const path = layoutUri('track-numbers', 'DRAFT');
    return await postIgnoreError<TrackNumberSaveRequest, LayoutTrackNumberId>(path, request).then(
        (rs) => updateTrackNumberChangeTime().then((_) => rs),
    );
}

export async function deleteTrackNumber(
    trackNumberId: LayoutTrackNumberId,
): Promise<Result<LocationTrackId, LocationTrackSaveError>> {
    const path = layoutUri('track-numbers', 'DRAFT', trackNumberId);
    const apiResult = await deleteAdt<undefined, LayoutTrackNumberId>(path, undefined, true);
    updateTrackNumberChangeTime();
    updateReferenceLineChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function getSwitchJointConnections(
    publishType: PublishType,
    id: LayoutSwitchId,
): Promise<LayoutSwitchJointConnection[]> {
    return getWithDefault<LayoutSwitchJointConnection[]>(
        `${layoutUri('switches', publishType, id)}/joint-connections`,
        [],
    );
}
