import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import { deleteNonNull, getNonNull, postNonNull, putNonNull, queryParams } from 'api/api-fetch';
import { asyncCache } from 'cache/cache';
import { MapTile } from 'map/map-model';
import { bboxString } from 'common/common-api';
import { LayoutAssetChangeInfo, LayoutContext, TimeStamp } from 'common/common-model';
import { brand } from 'common/brand';
import { contextInUri, layoutUri, TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';
import { updateOperationalPointsChangeTime } from 'common/change-time-api';
import { InternalOperationalPointSaveRequest } from 'tool-panel/operating-point/internal-operational-point-edit-store';
import { ExternalOperationalPointSaveRequest } from 'tool-panel/operating-point/external-operational-point-edit-store';

type OriginInUri = 'INTERNAL' | 'EXTERNAL';
type OperationalPointSaveRequest =
    | InternalOperationalPointSaveRequest
    | ExternalOperationalPointSaveRequest;

const cacheKey = (id: OperationalPointId, layoutContext: LayoutContext) =>
    `${id}_${layoutContext.publicationState}_${layoutContext.branch}`;

const operationalPointsCache = asyncCache<string, OperationalPoint>();
const operationalPointsTileCache = asyncCache<string, OperationalPoint[]>();

const operationalPointUriByOrigin = (
    id: OperationalPointId,
    origin: OriginInUri,
    layoutContext: LayoutContext,
) => `${TRACK_LAYOUT_URI}/operational-points/${origin}/${contextInUri(layoutContext)}/${id}`;

export async function getOperationalPoints(
    mapTile: MapTile,
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<OperationalPoint[]> {
    return operationalPointsTileCache.get(changeTime, mapTile.id, () => {
        const params = queryParams({ bbox: bboxString(mapTile.area) });

        return getNonNull<OperationalPoint[]>(
            `${layoutUri('operational-points', layoutContext)}${params}`,
        );
    });
}

export const getOperationalPoint = async (
    id: OperationalPointId,
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<OperationalPoint> =>
    operationalPointsCache.get(
        changeTime,
        cacheKey(id, layoutContext),
        // TODO use proper fetch
        () =>
            Promise.resolve({
                id: brand('INT_1'),
                origin: 'GEOVIITE',
                name: 'Helsinki Asema',
                abbreviation: 'HKI',
                uicCode: '0000001',
                state: 'IN_USE',
                raideType: 'LP',
                rinfType: 10,
                isDraft: false,
                dataType: 'STORED',
                version: 'a_b',
            }),
    );

export const getManyOperationalPoints = async (
    ids: OperationalPointId[],
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<OperationalPoint[]> =>
    Promise.all(ids.map((id) => getOperationalPoint(id, layoutContext, changeTime)));

export const getOperationalPointChangeTimes = (
    _id: OperationalPointId,
    _layoutContext: LayoutContext,
): Promise<LayoutAssetChangeInfo | undefined> =>
    // TODO use proper fetch
    Promise.resolve({
        created: '2023-10-10T12:00:00Z',
        changed: '2023-10-15T12:00:00Z',
    });

export async function insertOperationalPoint(
    newOperationalPoint: InternalOperationalPointSaveRequest,
    layoutContext: LayoutContext,
): Promise<OperationalPointId> {
    const result = await postNonNull<InternalOperationalPointSaveRequest, OperationalPointId>(
        layoutUri('operational-points', layoutContext),
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
        operationalPointUriByOrigin(id, origin, layoutContext),
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
    updateOperationalPoint(id, 'INTERNAL', updatedOperationalPoint, layoutContext);

export const updateExternalOperationalPoint = (
    id: OperationalPointId,
    updatedOperationalPoint: ExternalOperationalPointSaveRequest,
    layoutContext: LayoutContext,
): Promise<OperationalPointId> =>
    updateOperationalPoint(id, 'EXTERNAL', updatedOperationalPoint, layoutContext);

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
