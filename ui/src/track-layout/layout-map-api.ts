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
import {
    draftLayoutContext,
    LayoutContext,
    RowVersion,
    TimeStamp,
    TrackMeter,
} from 'common/common-model';
import { LinkInterval, LinkPoint, MapAlignmentEndPoints } from 'linking/linking-model';
import { bboxString, pointString } from 'common/common-api';
import { getTrackLayoutPlan } from 'geometry/geometry-api';
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';
import { createLinkPoints, alignmentPointToLinkPoint } from 'linking/linking-store';
import {
    deduplicate,
    filterNotEmpty,
    first,
    indexIntoMap,
    last,
    partitionBy,
} from 'utils/array-utils';
import { getMaxTimestamp } from 'utils/date-utils';
import { getTrackNumbers } from './layout-track-number-api';
import { directionBetweenPoints } from 'utils/math-utils';
import { ChangeTimes } from 'common/common-slice';

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

function mapUri(layoutContext: LayoutContext): string {
    return `${TRACK_LAYOUT_URI}/map/${layoutContext.publicationState.toLowerCase()}`;
}

function mapAlignmentUri(
    layoutContext: LayoutContext,
    alignmentType: MapAlignmentType,
    content?: string,
): string {
    const type = alignmentType == 'LOCATION_TRACK' ? 'location-track' : 'reference-line';
    const baseUri = `${TRACK_LAYOUT_URI}/map/${layoutContext.publicationState.toLowerCase()}/${type}`;
    return content ? `${baseUri}/${content}` : baseUri;
}

function geocodingUri(layoutContext: LayoutContext): string {
    return `${GEOCODING_URI}/${layoutContext.publicationState.toLowerCase()}`;
}

function cacheKey(id: ReferenceLineId | LocationTrackId, layoutContext: LayoutContext) {
    return `${id}_${layoutContext.publicationState}_${layoutContext.designId}`;
}

export async function getMapAlignmentsByTiles(
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
): Promise<AlignmentDataHolder[]> {
    const polyLines = await Promise.all(
        mapTiles.map((tile) =>
            getPolyLines(tile, changeTimes.layoutReferenceLine, layoutContext, 'ALL'),
        ),
    ).then((p) => p.flat());

    const [rlPolyLines, ltPolyLines] = partitionBy(
        polyLines,
        (i) => i.alignmentType === 'REFERENCE_LINE',
    );

    return [
        ...(await getAlignmentDataHolder(
            'REFERENCE_LINE',
            rlPolyLines,
            layoutContext,
            changeTimes,
        )),
        ...(await getAlignmentDataHolder(
            'LOCATION_TRACK',
            ltPolyLines,
            layoutContext,
            changeTimes,
        )),
    ];
}

export async function getSelectedReferenceLineMapAlignmentByTiles(
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
    trackNumberId: LayoutTrackNumberId,
): Promise<AlignmentDataHolder[]> {
    return getReferenceLineMapAlignmentsByTiles(changeTimes, mapTiles, layoutContext).then(
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
    layoutContext: LayoutContext,
): Promise<AlignmentDataHolder[]> {
    const changeTime = getMaxTimestamp(
        changeTimes.layoutReferenceLine,
        changeTimes.layoutTrackNumber,
    );
    const polyLines = await Promise.all(
        mapTiles.map((tile) => getPolyLines(tile, changeTime, layoutContext, 'REFERENCE_LINES')),
    ).then((p) => p.flat());

    return getAlignmentDataHolder('REFERENCE_LINE', polyLines, layoutContext, changeTimes);
}

export async function getSelectedLocationTrackMapAlignmentByTiles(
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
    locationTrackId: LocationTrackId,
): Promise<AlignmentDataHolder[]> {
    const polyLines = await Promise.all(
        mapTiles.map((tile) =>
            getLocationTrackPolyline(
                locationTrackId,
                tile,
                changeTimes.layoutLocationTrack,
                layoutContext,
            ).then((r) => (r ? [r] : [])),
        ),
    ).then((lines) => lines.flat());

    return getAlignmentDataHolder('LOCATION_TRACK', polyLines, layoutContext, changeTimes);
}

export async function getLocationTrackMapAlignmentsByTiles(
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
): Promise<AlignmentDataHolder[]> {
    const polyLines = await Promise.all(
        mapTiles.map((tile) =>
            getPolyLines(tile, changeTimes.layoutLocationTrack, layoutContext, 'LOCATION_TRACKS'),
        ),
    ).then((lines) => lines.flat());

    return getAlignmentDataHolder('LOCATION_TRACK', polyLines, layoutContext, changeTimes);
}

async function getAlignmentDataHolder(
    type: MapAlignmentType,
    polyLines: AlignmentPolyLine[],
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): Promise<AlignmentDataHolder[]> {
    const ids = deduplicate(polyLines.map((pl) => pl.id));
    const headerChangeTime =
        type === 'LOCATION_TRACK'
            ? changeTimes.layoutLocationTrack
            : getMaxTimestamp(changeTimes.layoutTrackNumber, changeTimes.layoutReferenceLine);

    const headers = await getAlignmentHeaders(layoutContext, ids, type, headerChangeTime);
    const trackNumbers = await getTrackNumbers(layoutContext, changeTimes.layoutTrackNumber);

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
    layoutContext: LayoutContext,
    mapTiles: MapTile[],
): Promise<AlignmentHighlight[]> {
    return (
        await Promise.all(
            mapTiles.map((tile) =>
                getLocationTrackSectionsWithoutProfileByTile(changeTime, layoutContext, tile),
            ),
        )
    ).flat();
}

function getLocationTrackSectionsWithoutProfileByTile(
    changeTime: TimeStamp,
    layoutContext: LayoutContext,
    mapTile: MapTile,
): Promise<AlignmentHighlight[]> {
    const tileKey = `${mapTile.id}_${layoutContext.publicationState}_${layoutContext.designId}`;
    const params = queryParams({ bbox: bboxString(mapTile.area) });
    return sectionsWithoutProfileCache.get(changeTime, tileKey, () =>
        getNonNull(`${mapUri(layoutContext)}/location-track/without-profile${params}`),
    );
}

export async function getAlignmentSectionsWithoutLinkingByTiles(
    changeTime: TimeStamp,
    layoutContext: LayoutContext,
    type: AlignmentFetchType,
    mapTiles: MapTile[],
): Promise<AlignmentHighlight[]> {
    return (
        await Promise.all(
            mapTiles.map((tile) =>
                getAlignmentSectionsWithoutLinkingByTile(changeTime, layoutContext, type, tile),
            ),
        )
    ).flat();
}

async function getAlignmentSectionsWithoutLinkingByTile(
    changeTime: TimeStamp,
    layoutContext: LayoutContext,
    type: AlignmentFetchType,
    mapTile: MapTile,
): Promise<AlignmentHighlight[]> {
    const tileKey = `${mapTile.id}_${type}_${layoutContext.publicationState}_${layoutContext.designId}`;
    const params = queryParams({ bbox: bboxString(mapTile.area), type: type });
    return sectionsWithoutLinkingCache.get(changeTime, tileKey, () =>
        getNonNull(`${mapUri(layoutContext)}/alignment/without-linking${params}`),
    );
}

async function getAlignmentHeaders(
    layoutContext: LayoutContext,
    ids: (ReferenceLineId | LocationTrackId)[],
    type: MapAlignmentType,
    changeTime: TimeStamp,
): Promise<AlignmentHeader[]> {
    return (type === 'LOCATION_TRACK' ? locationTrackHeaderCache : referenceLineHeaderCache)
        .getMany(
            changeTime,
            ids,
            (id) => cacheKey(id, layoutContext),
            (fetchIds) =>
                getNonNull<AlignmentHeader[]>(
                    `${mapAlignmentUri(layoutContext, type, 'alignment-headers')}?ids=${fetchIds}`,
                ).then((headers) => {
                    const headerMap = indexIntoMap(headers);
                    return (id) => headerMap.get(id);
                }),
        )
        .then((headers) => headers.filter(filterNotEmpty));
}

export async function getSegmentEnds(
    layoutContext: LayoutContext,
    id: ReferenceLineId,
    type: MapAlignmentType,
    changeTime: TimeStamp,
): Promise<number[]> {
    return (
        type === 'LOCATION_TRACK' ? locationTrackSegmentMCache : referenceLineSegmentMCache
    ).get(changeTime, cacheKey(id, layoutContext), () =>
        getNonNull<number[]>(mapAlignmentUri(layoutContext, type, `${id}/segment-m`)),
    );
}

export async function getEndLinkPoints(
    id: ReferenceLineId,
    layoutContext: LayoutContext,
    type: MapAlignmentType,
    changeTime: TimeStamp,
): Promise<LinkInterval> {
    const alignmentEndpointsToLinkPoint = (end: AlignmentPoint, next: AlignmentPoint) =>
        alignmentPointToLinkPoint(type, id, end, directionBetweenPoints(end, next), true, true);

    return (type === 'LOCATION_TRACK' ? locationTrackEndsCache : referenceLineEndsCache)
        .get(changeTime, cacheKey(id, layoutContext), () =>
            getNonNull<MapAlignmentEndPoints>(mapAlignmentUri(layoutContext, type, `${id}/ends`)),
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
    layoutContext: LayoutContext,
    mapTiles: MapTile[],
    alignmentId: AlignmentId,
    alignmentType: MapAlignmentType,
): Promise<LinkPoint[]> {
    const segmentEndMs = await getSegmentEnds(
        draftLayoutContext(layoutContext),
        alignmentId,
        alignmentType,
        changeTime,
    );
    const tiledAlignments = await Promise.all(
        mapTiles.map((tile) =>
            getPolyLines(tile, changeTime, draftLayoutContext(layoutContext), 'ALL'),
        ),
    );
    const allPieces = tiledAlignments
        .flat()
        .filter((a) => a.alignmentType === alignmentType && a.id === alignmentId);
    const allPoints = combineAlignmentPoints(allPieces.map((a) => a.points));
    const lastSegmentEndM = last(segmentEndMs);
    return lastSegmentEndM
        ? createLinkPoints(alignmentType, alignmentId, lastSegmentEndM, segmentEndMs, allPoints)
        : [];
}

export async function getGeometryLinkPointsByTiles(
    geometryPlanId: GeometryPlanId,
    geometryAlignmentId: GeometryAlignmentId,
    mapTiles: MapTile[],
    alwaysIncludePoints: LinkPoint[] = [],
    changeTime: TimeStamp = getChangeTimes().geometryPlan,
): Promise<LinkPoint[]> {
    const firstMapTile = first(mapTiles);
    if (!firstMapTile) return [];

    const resolution = toMapAlignmentResolution(firstMapTile.resolution);
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
    layoutContext: LayoutContext,
    fetchType: AlignmentFetchType,
): Promise<AlignmentPolyLine[]> {
    const tileKey = `${mapTile.id}_${layoutContext.publicationState}_${layoutContext.designId}_${fetchType}`;
    const params = queryParams({
        resolution: toMapAlignmentResolution(mapTile.resolution),
        bbox: bboxString(mapTile.area),
        type: fetchType.toUpperCase(),
    });
    return await alignmentPolyLinesCache.get(changeTime, tileKey, () =>
        getNonNull<AlignmentPolyLine[]>(`${mapUri(layoutContext)}/alignment-polylines${params}`),
    );
}

async function getLocationTrackPolyline(
    locationTrackId: LocationTrackId,
    mapTile: MapTile,
    changeTime: TimeStamp,
    layoutContext: LayoutContext,
): Promise<AlignmentPolyLine | undefined> {
    const tileKey = `${locationTrackId}_${mapTile.id}_${layoutContext.publicationState}_${layoutContext.designId}`;
    const params = queryParams({
        resolution: toMapAlignmentResolution(mapTile.resolution),
        bbox: bboxString(mapTile.area),
    });

    return await locationTrackPolyLineCache.get(changeTime, tileKey, () =>
        getNullable<AlignmentPolyLine>(
            `${mapUri(layoutContext)}/location-track/${locationTrackId}/alignment-polyline${params}`,
        ),
    );
}

export function toMapAlignmentResolution(tileResolution: number): number {
    return parseFloat(Math.ceil(tileResolution * MAP_RESOLUTION_MULTIPLIER).toPrecision(1));
}

export async function getTrackMeter(
    trackNumberId: string,
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
    location: Point,
): Promise<TrackMeter | undefined> {
    const params = queryParams({ coordinate: pointString(location) });

    return trackNumberTrackMeterCache.get(
        changeTime,
        `${trackNumberId}_${layoutContext.publicationState}_${pointString(location)}`,
        () => {
            return getNullable<TrackMeter>(
                `${geocodingUri(layoutContext)}/address/${trackNumberId}${params}`,
            );
        },
    );
}
