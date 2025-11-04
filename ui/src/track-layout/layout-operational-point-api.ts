import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import {
    deleteNonNull,
    getNonNull,
    getNullable,
    postNonNull,
    putNonNull,
    queryParams,
} from 'api/api-fetch';
import { asyncCache } from 'cache/cache';
import { MapTile } from 'map/map-model';
import { bboxString } from 'common/common-api';
import {
    DesignBranch,
    LayoutAssetChangeInfo,
    LayoutBranch,
    LayoutContext,
    Oid,
    TimeStamp,
} from 'common/common-model';
import {
    contextInUri,
    layoutUri,
    layoutUriByBranch,
    layoutUriWithoutContext,
    TRACK_LAYOUT_URI,
} from 'track-layout/track-layout-api';
import { getChangeTimes, updateOperationalPointsChangeTime } from 'common/change-time-api';
import { InternalOperationalPointSaveRequest } from 'tool-panel/operational-point/internal-operational-point-edit-store';
import { ExternalOperationalPointSaveRequest } from 'tool-panel/operational-point/external-operational-point-edit-store';
import { filterNotEmpty, indexIntoMap } from 'utils/array-utils';
import { Polygon, Point } from 'model/geometry';

type OriginInUri = 'internal' | 'external';
type OperationalPointSaveRequest =
    | InternalOperationalPointSaveRequest
    | ExternalOperationalPointSaveRequest;

const singlePointCacheKey = (id: OperationalPointId, layoutContext: LayoutContext) =>
    `${id}_${layoutContext.publicationState}_${layoutContext.branch}`;
const mapTileCacheKey = (mapTileId: string, layoutContext: LayoutContext) =>
    `${mapTileId}_${layoutContext.publicationState}_${layoutContext.branch}`;

const operationalPointsCache = asyncCache<string, OperationalPoint | undefined>();
const allOperationalPointsCache = asyncCache<string, OperationalPoint[]>();
const operationalPointsTileCache = asyncCache<string, OperationalPoint[]>();
const operationalPointOidsCache = asyncCache<
    OperationalPointId,
    { [key in LayoutBranch]?: Oid } | undefined
>();

const operationalPointUriByOrigin = (
    origin: OriginInUri,
    layoutContext: LayoutContext,
    id: OperationalPointId | undefined = undefined,
) => {
    const base = `${TRACK_LAYOUT_URI}/operational-points/${origin}/${contextInUri(layoutContext)}`;
    return id ? `${base}/${id}` : base;
};

export async function getOperationalPoints(
    mapTile: MapTile,
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<OperationalPoint[]> {
    return operationalPointsTileCache.get(
        changeTime,
        mapTileCacheKey(mapTile.id, layoutContext),
        () => {
            const params = queryParams({ bbox: bboxString(mapTile.area) });

            return getNonNull<OperationalPoint[]>(
                `${layoutUri('operational-points', layoutContext)}${params}`,
            );
        },
    );
}

export const getOperationalPoint = async (
    id: OperationalPointId,
    layoutContext: LayoutContext,
    changeTime: TimeStamp = getChangeTimes().operationalPoints,
): Promise<OperationalPoint | undefined> =>
    operationalPointsCache.get(changeTime, singlePointCacheKey(id, layoutContext), () =>
        getNullable<OperationalPoint>(`${layoutUri('operational-points', layoutContext)}/${id}`),
    );

export const getManyOperationalPoints = async (
    ids: OperationalPointId[],
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<OperationalPoint[]> =>
    operationalPointsCache
        .getMany(
            changeTime,
            ids,
            (id) => singlePointCacheKey(id, layoutContext),
            (ids) =>
                getNonNull<OperationalPoint[]>(
                    `${layoutUri('operational-points', layoutContext)}${queryParams({
                        ids,
                    })}`,
                ).then((ops) => {
                    const opMap = indexIntoMap(ops);
                    return (id) => opMap.get(id);
                }),
        )
        .then((ops) => ops.filter(filterNotEmpty));

export const getAllOperationalPoints = (
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<OperationalPoint[]> =>
    allOperationalPointsCache.get(
        changeTime,
        `${layoutContext.branch}_${layoutContext.publicationState}`,
        () => getNonNull<OperationalPoint[]>(`${layoutUri('operational-points', layoutContext)}`),
    );

export const getOperationalPointChangeTimes = (
    id: OperationalPointId,
    layoutContext: LayoutContext,
): Promise<LayoutAssetChangeInfo | undefined> =>
    getNonNull<LayoutAssetChangeInfo>(
        `${layoutUri('operational-points', layoutContext)}/${id}/change-info`,
    );

export async function insertOperationalPoint(
    newOperationalPoint: InternalOperationalPointSaveRequest,
    layoutContext: LayoutContext,
): Promise<OperationalPointId> {
    const result = await postNonNull<InternalOperationalPointSaveRequest, OperationalPointId>(
        operationalPointUriByOrigin('internal', layoutContext),
        newOperationalPoint,
    );
    await updateOperationalPointsChangeTime();
    return result;
}

async function updateOperationalPoint(
    id: OperationalPointId,
    origin: OriginInUri,
    updatedOperationalPoint: OperationalPointSaveRequest,
    layoutContext: LayoutContext,
): Promise<OperationalPointId> {
    const result = await putNonNull<OperationalPointSaveRequest, OperationalPointId>(
        operationalPointUriByOrigin(origin, layoutContext, id),
        updatedOperationalPoint,
    );
    await updateOperationalPointsChangeTime();
    return result;
}

export const updateInternalOperationalPoint = (
    id: OperationalPointId,
    updatedOperationalPoint: InternalOperationalPointSaveRequest,
    layoutContext: LayoutContext,
): Promise<OperationalPointId> =>
    updateOperationalPoint(id, 'internal', updatedOperationalPoint, layoutContext);

export const updateInternalOperationalPointLocation = async (
    id: OperationalPointId,
    location: Point,
    layoutContext: LayoutContext,
): Promise<OperationalPointId> => {
    const result = await putNonNull<Point, OperationalPointId>(
        `${operationalPointUriByOrigin('internal', layoutContext, id)}/location`,
        location,
    );
    await updateOperationalPointsChangeTime();
    return result;
};

export const updateExternalOperationalPoint = (
    id: OperationalPointId,
    updatedOperationalPoint: ExternalOperationalPointSaveRequest,
    layoutContext: LayoutContext,
): Promise<OperationalPointId> =>
    updateOperationalPoint(id, 'external', updatedOperationalPoint, layoutContext);

export const updateOperationalPointArea = async (
    id: OperationalPointId,
    polygon: Polygon,
    layoutContext: LayoutContext,
): Promise<OperationalPointId> => {
    const result = await putNonNull<Polygon, OperationalPointId>(
        `${layoutUri('operational-points', layoutContext, id)}/location`,
        polygon,
    );
    await updateOperationalPointsChangeTime();
    return result;
};

export async function deleteDraftOperationalPoint(
    layoutContext: LayoutContext,
    id: OperationalPointId,
): Promise<OperationalPointId | undefined> {
    const result = await deleteNonNull<OperationalPointId>(
        layoutUri('operational-points', layoutContext, id),
    );
    await updateOperationalPointsChangeTime();
    return result;
}

export async function getOperationalPointOids(
    id: OperationalPointId,
    changeTime: TimeStamp,
): Promise<{ [key in LayoutBranch]?: Oid }> {
    const oids = await operationalPointOidsCache.get(changeTime, id, () =>
        getNullable<{ [key in LayoutBranch]?: Oid }>(
            `${layoutUriWithoutContext('operational-points', id)}/oids`,
        ),
    );
    return oids ?? {};
}

export const cancelOperationalPoint = async (
    design: DesignBranch,
    id: OperationalPointId,
): Promise<void> =>
    postNonNull(`${layoutUriByBranch('operational-points', design)}/${id}/cancel`, '');
