import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import { API_URI, getNonNull, queryParams } from 'api/api-fetch';
import { asyncCache } from 'cache/cache';
import { MapTile } from 'map/map-model';
import { bboxString } from 'common/common-api';
import { LayoutContext, TimeStamp } from 'common/common-model';
import { brand } from 'common/brand';

const cacheKey = (id: OperationalPointId, layoutContext: LayoutContext) =>
    `${id}_${layoutContext.publicationState}_${layoutContext.branch}`;

const operationalPointsCache = asyncCache<string, OperationalPoint>();
const operationalPointsTileCache = asyncCache<string, OperationalPoint[]>();

export async function getOperationalPoints(
    mapTile: MapTile,
    _layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<OperationalPoint[]> {
    return operationalPointsTileCache.get(changeTime, mapTile.id, () => {
        const params = queryParams({ bbox: bboxString(mapTile.area) });

        // New url once it works:
        //return getNonNull<OperationalPoint[]>(`${API_URI}/operational-points/${layoutContext.branch}/${layoutContext.publicationState}/${params}`);
        return getNonNull<OperationalPoint[]>(`${API_URI}/ratko/operational-points${params}`);
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
        () =>
            Promise.resolve({
                id: brand('INT_1'),
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

        /*getNonNull<OperationalPoint>(
            `${API_URI}/operational-points/${layoutContext.branch}/${layoutContext.publicationState}/${id}`,
        ),*/
    );

export const getManyOperationalPoints = async (
    ids: OperationalPointId[],
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<OperationalPoint[]> =>
    Promise.all(ids.map((id) => getOperationalPoint(id, layoutContext, changeTime)));
