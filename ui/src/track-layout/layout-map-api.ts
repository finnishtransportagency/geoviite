import { asyncCache } from 'cache/cache';
import { AlignmentHighlight, MapTile } from 'map/map-model';
import {
    AlignmentId,
    combineAlignmentPoints,
    AlignmentPoint,
    LayoutState,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    LocationTrackType,
    MapAlignmentSource,
    MapAlignmentType,
    ReferenceLineId,
} from './track-layout-model';
import { API_URI, getNonNull, getNullable, queryParams } from 'api/api-fetch';
import { BoundingBox, boundingBoxContains, combineBoundingBoxes, Point } from 'model/geometry';
import { MAP_RESOLUTION_MULTIPLIER } from 'map/layers/utils/layer-visibility-limits';
import { getChangeTimes } from 'common/change-time-api';
import { PublishType, RowVersion, TimeStamp, TrackMeter } from 'common/common-model';
import { LinkInterval, LinkPoint, MapAlignmentEndPoints } from 'linking/linking-model';
import { bboxString, pointString } from 'common/common-api';
import { getTrackLayoutPlan } from 'geometry/geometry-api';
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';
import { createLinkPoints, alignmentPointToLinkPoint } from 'linking/linking-store';
import { deduplicate, filterNotEmpty, indexIntoMap, partitionBy } from 'utils/array-utils';
import { getMaxTimestamp } from 'utils/date-utils';
import { getTrackNumbers } from './layout-track-number-api';
import { directionBetweenPoints } from 'utils/math-utils';
import { ChangeTimes } from 'common/common-slice';
import { getUnsafe } from 'utils/type-utils';

export type AlignmentDataHolder = {
    trackNumber?: LayoutTrackNumber;
    header: AlignmentHeader;
    points: AlignmentPoint[];
    planId?: GeometryPlanId;
};

export type AlignmentHeader = {
    id: LocationTrackId | ReferenceLineId | GeometryAlignmentId;
    version?: RowVersion;
    trackNumberId?: LayoutTrackNumberId;
    duplicateOf?: LocationTrackId;

    name: string;
    state: LayoutState;
    alignmentSource: MapAlignmentSource;
    alignmentType: MapAlignmentType;
    trackType?: LocationTrackType;

    length: number;
    boundingBox?: BoundingBox;
};

export type AlignmentPolyLine = {
    id: LocationTrackId | ReferenceLineId;
    alignmentType: MapAlignmentType;
    points: AlignmentPoint[];
};

const referenceLineHeaderCache = asyncCache<string, AlignmentHeader | undefined>();
const locationTrackHeaderCache = asyncCache<string, AlignmentHeader | undefined>();
const alignmentPolyLinesCache = asyncCache<string, AlignmentPolyLine[]>();
const locationTrackPolyLineCache = asyncCache<string, AlignmentPolyLine | undefined>();

const locationTrackSegmentMCache = asyncCache<string, number[]>();
const referenceLineSegmentMCache = asyncCache<string, number[]>();

const locationTrackEndsCache = asyncCache<string, MapAlignmentEndPoints>();
const referenceLineEndsCache = asyncCache<string, MapAlignmentEndPoints>();
const sectionsWithoutProfileCache = asyncCache<string, AlignmentHighlight[]>();
const sectionsWithoutLinkingCache = asyncCache<string, AlignmentHighlight[]>();
const trackNumberTrackMeterCache = asyncCache<LayoutTrackNumberId, TrackMeter | undefined>();

export const GEOCODING_URI = `${API_URI}/geocoding`;

export type AlignmentFetchType = 'LOCATION_TRACKS' | 'REFERENCE_LINES' | 'ALL';

function mapUri(publishType: PublishType): string {
    return `${TRACK_LAYOUT_URI}/map/${publishType.toLowerCase()}`;
}

function mapAlignmentUri(
    publishType: PublishType,
    alignmentType: MapAlignmentType,
    content?: string,
): string {
    const type = alignmentType == 'LOCATION_TRACK' ? 'location-track' : 'reference-line';
    const baseUri = `${TRACK_LAYOUT_URI}/map/${publishType.toLowerCase()}/${type}`;
    return content ? `${baseUri}/${content}` : baseUri;
}

function geocodingUri(publishType: PublishType) {
    return `${GEOCODING_URI}/${publishType.toLowerCase()}`;
}

function cacheKey(id: ReferenceLineId | LocationTrackId, publishType: PublishType) {
    return `${id}_${publishType}`;
}

export async function getMapAlignmentsByTiles(
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    publishType: PublishType,
) {
    const polyLines = await Promise.all(
        mapTiles.map((tile) =>
            getPolyLines(tile, changeTimes.layoutReferenceLine, publishType, 'ALL'),
        ),
    ).then((p) => p.flat());

    const [rlPolyLines, ltPolyLines] = partitionBy(
        polyLines,
        (i) => i.alignmentType === 'REFERENCE_LINE',
    );

    return [
        ...(await getAlignmentDataHolder('REFERENCE_LINE', rlPolyLines, publishType, changeTimes)),
        ...(await getAlignmentDataHolder('LOCATION_TRACK', ltPolyLines, publishType, changeTimes)),
    ];
}

export async function getSelectedReferenceLineMapAlignmentByTiles(
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    publishType: PublishType,
    trackNumberId: LayoutTrackNumberId,
) {
    return getReferenceLineMapAlignmentsByTiles(changeTimes, mapTiles, publishType).then(
        (alignments) => {
            const alignment = alignments.find(
                (a) =>
                    a.header.trackNumberId === trackNumberId &&
                    a.header.alignmentType === 'REFERENCE_LINE',
            );
            return alignment ? [alignment] : [];
        },
    );
}

export async function getReferenceLineMapAlignmentsByTiles(
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    publishType: PublishType,
) {
    const changeTime = getMaxTimestamp(
        changeTimes.layoutReferenceLine,
        changeTimes.layoutTrackNumber,
    );
    const polyLines = await Promise.all(
        mapTiles.map((tile) => getPolyLines(tile, changeTime, publishType, 'REFERENCE_LINES')),
    ).then((p) => p.flat());

    return getAlignmentDataHolder('REFERENCE_LINE', polyLines, publishType, changeTimes);
}

export async function getSelectedLocationTrackMapAlignmentByTiles(
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    publishType: PublishType,
    locationTrackId: LocationTrackId,
): Promise<AlignmentDataHolder[]> {
    const polyLines = await Promise.all(
        mapTiles.map((tile) =>
            getLocationTrackPolyline(
                locationTrackId,
                tile,
                changeTimes.layoutLocationTrack,
                publishType,
            ).then((r) => (r ? [r] : [])),
        ),
    ).then((lines) => lines.flat());

    return getAlignmentDataHolder('LOCATION_TRACK', polyLines, publishType, changeTimes);
}

export async function getLocationTrackMapAlignmentsByTiles(
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    publishType: PublishType,
): Promise<AlignmentDataHolder[]> {
    const polyLines = await Promise.all(
        mapTiles.map((tile) =>
            getPolyLines(tile, changeTimes.layoutLocationTrack, publishType, 'LOCATION_TRACKS'),
        ),
    ).then((lines) => lines.flat());

    return getAlignmentDataHolder('LOCATION_TRACK', polyLines, publishType, changeTimes);
}

async function getAlignmentDataHolder(
    type: MapAlignmentType,
    polyLines: AlignmentPolyLine[],
    publishType: PublishType,
    changeTimes: ChangeTimes,
): Promise<AlignmentDataHolder[]> {
    const ids = deduplicate(polyLines.map((pl) => pl.id));
    const headerChangeTime =
        type === 'LOCATION_TRACK'
            ? changeTimes.layoutLocationTrack
            : getMaxTimestamp(changeTimes.layoutTrackNumber, changeTimes.layoutReferenceLine);

    const headers = await getAlignmentHeaders(publishType, ids, type, headerChangeTime);
    const trackNumbers = await getTrackNumbers(publishType, changeTimes.layoutTrackNumber);

    return combine(headers, polyLines, trackNumbers);
}

function combine(
    headers: AlignmentHeader[],
    polyLines: AlignmentPolyLine[],
    trackNumbers: LayoutTrackNumber[],
): AlignmentDataHolder[] {
    return headers
        .map((header: AlignmentHeader) => {
            const polyLinePieces = polyLines.filter(
                (pl) => pl.id === header.id && pl.alignmentType === header.alignmentType,
            );
            return {
                header: header,
                points: combineAlignmentPoints(polyLinePieces.map((p) => p.points)),
                trackNumber: trackNumbers.find((tn) => tn.id === header.trackNumberId),
                planId: undefined,
            };
        })
        .filter(filterNotEmpty);
}

export async function getLocationTrackSectionsWithoutProfileByTiles(
    changeTime: TimeStamp,
    publishType: PublishType,
    mapTiles: MapTile[],
): Promise<AlignmentHighlight[]> {
    return (
        await Promise.all(
            mapTiles.map((tile) =>
                getLocationTrackSectionsWithoutProfileByTile(changeTime, publishType, tile),
            ),
        )
    ).flat();
}

function getLocationTrackSectionsWithoutProfileByTile(
    changeTime: TimeStamp,
    publishType: PublishType,
    mapTile: MapTile,
): Promise<AlignmentHighlight[]> {
    const tileKey = `${mapTile.id}_${publishType}}`;
    const params = queryParams({ bbox: bboxString(mapTile.area) });
    return sectionsWithoutProfileCache.get(changeTime, tileKey, () =>
        getNonNull(`${mapUri(publishType)}/location-track/without-profile${params}`),
    );
}

export async function getAlignmentSectionsWithoutLinkingByTiles(
    changeTime: TimeStamp,
    publishType: PublishType,
    type: AlignmentFetchType,
    mapTiles: MapTile[],
): Promise<AlignmentHighlight[]> {
    return (
        await Promise.all(
            mapTiles.map((tile) =>
                getAlignmentSectionsWithoutLinkingByTile(changeTime, publishType, type, tile),
            ),
        )
    ).flat();
}

async function getAlignmentSectionsWithoutLinkingByTile(
    changeTime: TimeStamp,
    publishType: PublishType,
    type: AlignmentFetchType,
    mapTile: MapTile,
): Promise<AlignmentHighlight[]> {
    const tileKey = `${mapTile.id}_${type}_${publishType}}`;
    const params = queryParams({ bbox: bboxString(mapTile.area), type: type });
    return sectionsWithoutLinkingCache.get(changeTime, tileKey, () =>
        getNonNull(`${mapUri(publishType)}/alignment/without-linking${params}`),
    );
}

async function getAlignmentHeaders(
    publishType: PublishType,
    ids: (ReferenceLineId | LocationTrackId)[],
    type: MapAlignmentType,
    changeTime: TimeStamp,
): Promise<AlignmentHeader[]> {
    return (type === 'LOCATION_TRACK' ? locationTrackHeaderCache : referenceLineHeaderCache)
        .getMany(
            changeTime,
            ids,
            (id) => cacheKey(id, publishType),
            (fetchIds) =>
                getNonNull<AlignmentHeader[]>(
                    `${mapAlignmentUri(publishType, type, 'alignment-headers')}?ids=${fetchIds}`,
                ).then((headers) => {
                    const headerMap = indexIntoMap(headers);
                    return (id) => headerMap.get(id);
                }),
        )
        .then((headers) => headers.filter(filterNotEmpty));
}

export async function getSegmentEnds(
    publishType: PublishType,
    id: ReferenceLineId,
    type: MapAlignmentType,
    changeTime: TimeStamp,
): Promise<number[]> {
    return (
        type === 'LOCATION_TRACK' ? locationTrackSegmentMCache : referenceLineSegmentMCache
    ).get(changeTime, cacheKey(id, publishType), () =>
        getNonNull<number[]>(mapAlignmentUri(publishType, type, `${id}/segment-m`)),
    );
}

export async function getEndLinkPoints(
    id: ReferenceLineId,
    publishType: PublishType,
    type: MapAlignmentType,
    changeTime: TimeStamp,
): Promise<LinkInterval> {
    const alignmentEndpointsToLinkPoint = (end: AlignmentPoint, next: AlignmentPoint) =>
        alignmentPointToLinkPoint(type, id, end, directionBetweenPoints(end, next), true, true);

    return (type === 'LOCATION_TRACK' ? locationTrackEndsCache : referenceLineEndsCache)
        .get(changeTime, cacheKey(id, publishType), () =>
            getNonNull<MapAlignmentEndPoints>(mapAlignmentUri(publishType, type, `${id}/ends`)),
        )
        .then(({ start: startPoints, end: endPoints }) => ({
            start:
                startPoints[0] === undefined || startPoints[1] === undefined
                    ? undefined
                    : alignmentEndpointsToLinkPoint(startPoints[0], startPoints[1]),
            end:
                endPoints[0] === undefined || endPoints[1] === undefined || endPoints.length != 2
                    ? undefined
                    : alignmentEndpointsToLinkPoint(endPoints[1], endPoints[0]),
        }));
}

export async function getLinkPointsByTiles(
    changeTime: TimeStamp,
    mapTiles: MapTile[],
    alignmentId: AlignmentId,
    alignmentType: MapAlignmentType,
): Promise<LinkPoint[]> {
    const segmentEndMs = await getSegmentEnds('DRAFT', alignmentId, alignmentType, changeTime);
    const tiledAlignments = await Promise.all(
        mapTiles.map((tile) => getPolyLines(tile, changeTime, 'DRAFT', 'ALL')),
    );
    const allPieces = tiledAlignments
        .flat()
        .filter((a) => a.alignmentType === alignmentType && a.id === alignmentId);
    const allPoints = combineAlignmentPoints(allPieces.map((a) => a.points));
    return createLinkPoints(
        alignmentType,
        alignmentId,
        getUnsafe(segmentEndMs[segmentEndMs.length - 1]),
        segmentEndMs,
        allPoints,
    );
}

export async function getGeometryLinkPointsByTiles(
    geometryPlanId: GeometryPlanId,
    geometryAlignmentId: GeometryAlignmentId,
    mapTiles: MapTile[],
    alwaysIncludePoints: LinkPoint[] = [],
    changeTime: TimeStamp = getChangeTimes().geometryPlan,
): Promise<LinkPoint[]> {
    if (!mapTiles[0]) return [];

    const resolution = toMapAlignmentResolution(mapTiles[0].resolution);
    const bounds = combineBoundingBoxes(mapTiles.map((tile) => tile.area));
    const plan = await getTrackLayoutPlan(geometryPlanId, changeTime, true);
    const alignment = plan?.alignments?.find((a) => a.header.id === geometryAlignmentId);
    if (alignment && alignment.polyLine) {
        const header = alignment.header;
        const polyLine = alignment.polyLine;
        const segmentMValues = alignment.segmentMValues;
        const points = polyLine.points.filter(
            (p) =>
                boundingBoxContains(bounds, p) &&
                (segmentMValues.includes(p.m) ||
                    resolution <= 1 ||
                    Math.floor(p.m) % resolution == 0 ||
                    alwaysIncludePoints.some((alwaysIncludePoint) => alwaysIncludePoint.m == p.m)),
        );
        return createLinkPoints(
            header.alignmentType,
            header.id,
            header.length,
            segmentMValues,
            points,
        );
    } else {
        return [];
    }
}

async function getPolyLines(
    mapTile: MapTile,
    changeTime: TimeStamp,
    publishType: PublishType,
    fetchType: AlignmentFetchType,
): Promise<AlignmentPolyLine[]> {
    const tileKey = `${mapTile.id}_${publishType}_${fetchType}`;
    const params = queryParams({
        resolution: toMapAlignmentResolution(mapTile.resolution),
        bbox: bboxString(mapTile.area),
        type: fetchType.toUpperCase(),
    });
    return await alignmentPolyLinesCache.get(changeTime, tileKey, () =>
        getNonNull<AlignmentPolyLine[]>(`${mapUri(publishType)}/alignment-polylines${params}`),
    );
}

async function getLocationTrackPolyline(
    locationTrackId: LocationTrackId,
    mapTile: MapTile,
    changeTime: TimeStamp,
    publishType: PublishType,
): Promise<AlignmentPolyLine | undefined> {
    const tileKey = `${locationTrackId}_${mapTile.id}_${publishType}`;
    const params = queryParams({
        resolution: toMapAlignmentResolution(mapTile.resolution),
        bbox: bboxString(mapTile.area),
    });

    return await locationTrackPolyLineCache.get(changeTime, tileKey, () =>
        getNullable<AlignmentPolyLine>(
            `${mapUri(publishType)}/location-track/${locationTrackId}/alignment-polyline${params}`,
        ),
    );
}

export function toMapAlignmentResolution(tileResolution: number): number {
    return parseFloat(Math.ceil(tileResolution * MAP_RESOLUTION_MULTIPLIER).toPrecision(1));
}

export async function getTrackMeter(
    trackNumberId: string,
    publishType: PublishType,
    changeTime: TimeStamp,
    location: Point,
): Promise<TrackMeter | undefined> {
    const params = queryParams({ coordinate: pointString(location) });

    return trackNumberTrackMeterCache.get(
        changeTime,
        `${trackNumberId}_${publishType}_${pointString(location)}`,
        () => {
            return getNullable<TrackMeter>(
                `${geocodingUri(publishType)}/address/${trackNumberId}${params}`,
            );
        },
    );
}
