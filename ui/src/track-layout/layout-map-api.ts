import { asyncCache } from 'cache/cache';
import { AlignmentHighlight, MapTile } from 'map/map-model';
import {
    AlignmentPoint,
    combineAlignmentPoints,
    LayoutState,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    LocationTrackState,
    LocationTrackType,
    MapAlignmentType,
    ReferenceLineId,
} from './track-layout-model';
import { API_URI, getNonNull, getNullable, queryParams } from 'api/api-fetch';
import { BoundingBox, boundingBoxContains, combineBoundingBoxes, Point } from 'model/geometry';
import {
    ALIGNMENT_MIN_LENGTH_IN_PIXELS,
    MAP_RESOLUTION_MULTIPLIER,
} from 'map/layers/utils/layer-visibility-limits';
import { getChangeTimes } from 'common/change-time-api';
import {
    draftLayoutContext,
    LayoutContext,
    RowVersion,
    TimeStamp,
    TrackMeter,
} from 'common/common-model';
import {
    LayoutAlignmentTypeAndId,
    LinkInterval,
    LinkPoint,
    MapAlignmentEndPoints,
} from 'linking/linking-model';
import { bboxString, pointString } from 'common/common-api';
import { getTrackLayoutPlan } from 'geometry/geometry-api';
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { contextInUri, TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';
import { alignmentPointToLinkPoint, createLinkPoints } from 'linking/linking-store';
import {
    deduplicate,
    filterNotEmpty,
    first,
    groupBy,
    indexIntoMap,
    last,
    partitionBy,
} from 'utils/array-utils';
import { getMaxTimestamp } from 'utils/date-utils';
import { getTrackNumbers } from './layout-track-number-api';
import { directionBetweenPoints } from 'utils/math-utils';
import { ChangeTimes } from 'common/common-slice';

export type LocationTrackAlignmentDataHolder = AlignmentDataHolder & {
    header: LocationTrackAlignmentHeader;
};
export type ReferenceLineAlignmentDataHolder = AlignmentDataHolder & {
    header: ReferenceLineAlignmentHeader;
};

export type LayoutAlignmentDataHolder = AlignmentDataHolder & { header: LayoutAlignmentHeader };
export type AlignmentDataHolder = {
    trackNumber?: LayoutTrackNumber;
    header: AlignmentHeader;
    points: AlignmentPoint[];
    planId?: GeometryPlanId;
};

export type LocationTrackAlignmentHeader = LayoutAlignmentHeader & {
    id: LocationTrackId;
    state: LocationTrackState;
};
export type ReferenceLineAlignmentHeader = LayoutAlignmentHeader & {
    id: ReferenceLineId;
    state: LayoutState;
};

type AlignmentHeaderIdAndType =
    | {
          id: LocationTrackId;
          alignmentSource: 'LAYOUT';
          alignmentType: 'LOCATION_TRACK';
      }
    | {
          id: ReferenceLineId;
          alignmentSource: 'LAYOUT';
          alignmentType: 'REFERENCE_LINE';
      }
    | {
          id: GeometryAlignmentId;
          alignmentSource: 'GEOMETRY';
          alignmentType: MapAlignmentType;
      };

export type LayoutAlignmentHeader = AlignmentHeader & {
    alignmentSource: 'LAYOUT';
};
export type AlignmentHeader = {
    version?: RowVersion;
    trackNumberId?: LayoutTrackNumberId;
    duplicateOf?: LocationTrackId;

    name: string;
    trackType?: LocationTrackType;
    state: LocationTrackState;

    length: number;
    boundingBox?: BoundingBox;
} & AlignmentHeaderIdAndType;

export type GeometryAlignmentHeader = AlignmentHeader & {
    id: GeometryAlignmentId;
    alignmentSource: 'GEOMETRY';
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
const locationTrackSectionsWithoutProfileCache = asyncCache<string, AlignmentHighlight[]>();
const sectionsWithoutLinkingCache = asyncCache<string, AlignmentHighlight[]>();
const trackNumberTrackMeterCache = asyncCache<string, TrackMeter | undefined>();

export const GEOCODING_URI = `${API_URI}/geocoding`;

export type AlignmentFetchType = 'LOCATION_TRACKS' | 'REFERENCE_LINES' | 'ALL';

function mapUri(layoutContext: LayoutContext): string {
    return `${TRACK_LAYOUT_URI}/map/${contextInUri(layoutContext)}`;
}

function mapAlignmentUri(
    layoutContext: LayoutContext,
    alignmentType: MapAlignmentType,
    content?: string,
): string {
    const type = alignmentType === 'LOCATION_TRACK' ? 'location-track' : 'reference-line';
    const baseUri = `${TRACK_LAYOUT_URI}/map/${contextInUri(layoutContext)}/${type}`;
    return content ? `${baseUri}/${content}` : baseUri;
}

function geocodingUri(layoutContext: LayoutContext): string {
    return `${GEOCODING_URI}/${contextInUri(layoutContext)}`;
}

function cacheKey(id: ReferenceLineId | LocationTrackId, layoutContext: LayoutContext) {
    return `${id}_${layoutContext.publicationState}_${layoutContext.branch}`;
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
            MapAlignmentType.ReferenceLine,
            rlPolyLines,
            layoutContext,
            changeTimes,
        )),
        ...(await getAlignmentDataHolder(
            MapAlignmentType.LocationTrack,
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
): Promise<ReferenceLineAlignmentDataHolder[]> {
    const changeTime = getMaxTimestamp(
        changeTimes.layoutReferenceLine,
        changeTimes.layoutTrackNumber,
    );
    const polyLines = await Promise.all(
        mapTiles.map((tile) => getPolyLines(tile, changeTime, layoutContext, 'REFERENCE_LINES')),
    ).then((p) => p.flat());

    return getAlignmentDataHolder(
        MapAlignmentType.ReferenceLine,
        polyLines,
        layoutContext,
        changeTimes,
    );
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

    return getAlignmentDataHolder(
        MapAlignmentType.LocationTrack,
        polyLines,
        layoutContext,
        changeTimes,
    );
}

export async function getLocationTrackMapAlignmentsByTiles(
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
    includeShortTracks: boolean = false,
    locationTrackIds: LocationTrackId[] | undefined = undefined,
): Promise<LocationTrackAlignmentDataHolder[]> {
    const polyLines = await Promise.all(
        mapTiles.map((tile) =>
            getPolyLines(
                tile,
                changeTimes.layoutLocationTrack,
                layoutContext,
                'LOCATION_TRACKS',
                false,
                includeShortTracks,
                locationTrackIds,
            ),
        ),
    ).then((lines) => lines.flat());

    return getAlignmentDataHolder(
        MapAlignmentType.LocationTrack,
        polyLines,
        layoutContext,
        changeTimes,
    );
}

type MapLayoutAlignmentDataHolder<M extends MapAlignmentType> = M extends 'LOCATION_TRACK'
    ? LocationTrackAlignmentDataHolder
    : M extends 'REFERENCE_LINE'
      ? ReferenceLineAlignmentDataHolder
      : never;

async function getAlignmentDataHolder<M extends MapAlignmentType>(
    type: M,
    polyLines: AlignmentPolyLine[],
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): Promise<MapLayoutAlignmentDataHolder<M>[]> {
    const ids = deduplicate(polyLines.map((pl) => pl.id));
    const headerChangeTime =
        type === 'LOCATION_TRACK'
            ? changeTimes.layoutLocationTrack
            : getMaxTimestamp(changeTimes.layoutTrackNumber, changeTimes.layoutReferenceLine);

    const headers = await getAlignmentHeaders(layoutContext, ids, type, headerChangeTime);
    const trackNumbers = await getTrackNumbers(layoutContext, changeTimes.layoutTrackNumber);

    return combine(headers, polyLines, trackNumbers) as MapLayoutAlignmentDataHolder<M>[];
}

function combine(
    headers: AlignmentHeader[],
    polyLines: AlignmentPolyLine[],
    trackNumbers: LayoutTrackNumber[],
): AlignmentDataHolder[] {
    const trackNumberMap: Map<LayoutTrackNumberId, LayoutTrackNumber> = indexIntoMap(trackNumbers);
    const { referenceLinePiecesMap, locationTrackPiecesMap } = indexPolylinesIntoMaps(polyLines);

    return headers
        .map((header: AlignmentHeader) => {
            const polyLinePieces =
                (header.alignmentType === 'REFERENCE_LINE'
                    ? referenceLinePiecesMap.get(header.id as ReferenceLineId)
                    : locationTrackPiecesMap.get(header.id as LocationTrackId)) ?? [];
            return {
                header: header,
                points: combineAlignmentPoints(polyLinePieces),
                trackNumber:
                    header.trackNumberId === undefined
                        ? undefined
                        : trackNumberMap.get(header.trackNumberId),
                planId: undefined,
            };
        })
        .filter(filterNotEmpty);
}

function indexPolylinesIntoMaps(polyLines: AlignmentPolyLine[]): {
    referenceLinePiecesMap: Map<ReferenceLineId, AlignmentPoint[][]>;
    locationTrackPiecesMap: Map<LocationTrackId, AlignmentPoint[][]>;
} {
    const referenceLinePiecesMap: Map<ReferenceLineId, AlignmentPoint[][]> = new Map();
    const locationTrackPiecesMap: Map<LocationTrackId, AlignmentPoint[][]> = new Map();
    for (const pl of polyLines) {
        switch (pl.alignmentType) {
            case MapAlignmentType.LocationTrack: {
                const id = pl.id as LocationTrackId;
                const elem = locationTrackPiecesMap.get(id);
                if (elem === undefined) {
                    locationTrackPiecesMap.set(id, [pl.points]);
                } else {
                    elem.push(pl.points);
                }
                break;
            }
            case MapAlignmentType.ReferenceLine: {
                const id = pl.id as ReferenceLineId;
                const elem = referenceLinePiecesMap.get(id);
                if (elem === undefined) {
                    referenceLinePiecesMap.set(id, [pl.points]);
                } else {
                    elem.push(pl.points);
                }
                break;
            }
        }
    }
    return { referenceLinePiecesMap, locationTrackPiecesMap };
}

export async function getLocationTrackSectionsWithoutProfile(
    changeTime: TimeStamp,
    layoutContext: LayoutContext,
    ids: LocationTrackId[],
): Promise<AlignmentHighlight[]> {
    return (
        await locationTrackSectionsWithoutProfileCache.getMany(
            changeTime,
            ids,
            (id) => `${id}_${layoutContext.publicationState}_${layoutContext.branch}`,
            (fetchIds) =>
                getNonNull<AlignmentHighlight[]>(
                    `${mapUri(layoutContext)}/location-track/without-profile?ids=${fetchIds}`,
                ).then((highlights) => {
                    const byTrack = groupBy(highlights, (hl) => hl.id as LocationTrackId);
                    return (id) => byTrack[id] ?? [];
                }),
        )
    )
        .filter(filterNotEmpty)
        .flat();
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
    const tileKey = `${mapTile.id}_${type}_${layoutContext.publicationState}_${layoutContext.branch}`;
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
    id: LocationTrackId | ReferenceLineId,
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
    id: LocationTrackId | ReferenceLineId,
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
                endPoints[0] === undefined || endPoints[1] === undefined || endPoints.length !== 2
                    ? undefined
                    : alignmentEndpointsToLinkPoint(endPoints[1], endPoints[0]),
        }));
}

export async function getLinkPointsByTiles(
    changeTime: TimeStamp,
    layoutContext: LayoutContext,
    mapTiles: MapTile[],
    alignment: LayoutAlignmentTypeAndId,
    includeSegmentEndPoints: boolean = false,
): Promise<LinkPoint[]> {
    const segmentEndMs = await getSegmentEnds(
        draftLayoutContext(layoutContext),
        alignment.id,
        alignment.type,
        changeTime,
    );
    const tiledAlignments = await Promise.all(
        mapTiles.map((tile) =>
            getPolyLines(
                tile,
                changeTime,
                draftLayoutContext(layoutContext),
                'ALL',
                includeSegmentEndPoints,
                true,
            ),
        ),
    );
    const allPieces = tiledAlignments
        .flat()
        .filter((a) => a.alignmentType === alignment.type && a.id === alignment.id);
    const allPoints = combineAlignmentPoints(allPieces.map((a) => a.points));
    const lastSegmentEndM = last(segmentEndMs);
    return lastSegmentEndM
        ? createLinkPoints(alignment, lastSegmentEndM, segmentEndMs, allPoints)
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

    const mapResolution = firstMapTile.resolution;
    const alignmentResolution = toMapAlignmentResolution(mapResolution);
    const bounds = combineBoundingBoxes(mapTiles.map((tile) => tile.area));
    const plan = (await getTrackLayoutPlan(geometryPlanId, changeTime, true)).layout;
    const alignment = plan?.alignments?.find((a) => a.header.id === geometryAlignmentId);
    if (alignment && alignment.polyLine) {
        const header = alignment.header;
        const polyLine = alignment.polyLine;
        const segmentMValues = alignment.segmentMValues;
        const points = polyLine.points.filter(
            (p) =>
                boundingBoxContains(bounds, p) &&
                (segmentMValues.includes(p.m) ||
                    alignmentResolution <= 1 ||
                    Math.floor(p.m) % alignmentResolution === 0 ||
                    alwaysIncludePoints.some((alwaysIncludePoint) => alwaysIncludePoint.m === p.m)),
        );
        return createLinkPoints(
            {
                type: header.alignmentType,
                id: header.id,
            },
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
    includeSegmentEndPoints: boolean = false,
    includeShortTracks: boolean = false,
    locationTrackIds: LocationTrackId[] | undefined = undefined,
): Promise<AlignmentPolyLine[]> {
    const locationTrackIdsStr = locationTrackIds?.join(',');
    const tileKey = `${mapTile.id}_${layoutContext.publicationState}_${layoutContext.branch}_${fetchType}_${includeSegmentEndPoints}_${includeShortTracks}_${locationTrackIdsStr}`;
    const tileResolution = mapTile.resolution;
    const alignmentResolution = toMapAlignmentResolution(tileResolution);

    // Tile resolution can be double the actual map resolution, therefore tile resolution is divided by two
    // to get correct min length, otherwise long enough tracks may be filtered out.
    const alignmentMinLength = getAlignmentMinLength(tileResolution / 2);

    const params = queryParams({
        resolution: alignmentResolution,
        bbox: bboxString(mapTile.area),
        type: fetchType.toUpperCase(),
        includeSegmentEndPoints: includeSegmentEndPoints,
        minLength: includeShortTracks ? undefined : alignmentMinLength,
        locationTrackIds: locationTrackIds,
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
    const tileKey = `${locationTrackId}_${mapTile.id}_${layoutContext.publicationState}_${layoutContext.branch}`;
    const params = queryParams({
        resolution: toMapAlignmentResolution(mapTile.resolution),
        bbox: bboxString(mapTile.area),
    });

    return await locationTrackPolyLineCache.get(changeTime, tileKey, () =>
        getNullable<AlignmentPolyLine>(
            `${mapUri(
                layoutContext,
            )}/location-track/${locationTrackId}/alignment-polyline${params}`,
        ),
    );
}

export function toMapAlignmentResolution(tileResolution: number): number {
    return parseFloat(Math.floor(tileResolution * MAP_RESOLUTION_MULTIPLIER).toPrecision(1));
}

export function getAlignmentMinLength(resolution: number) {
    return resolution * ALIGNMENT_MIN_LENGTH_IN_PIXELS;
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
        `${trackNumberId}_${layoutContext.publicationState}_${layoutContext.branch}_${pointString(
            location,
        )}`,
        () => {
            return getNullable<TrackMeter>(
                `${geocodingUri(layoutContext)}/address/${trackNumberId}${params}`,
            );
        },
    );
}
