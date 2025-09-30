import { OperationalPoint } from 'track-layout/track-layout-model';
import { API_URI, getNonNull, queryParams } from 'api/api-fetch';
import { asyncCache } from 'cache/cache';
import { MapTile } from 'map/map-model';
import { bboxString } from 'common/common-api';
import { TimeStamp } from 'common/common-model';

const operationalPointsCache = asyncCache<string, OperationalPoint[]>();

export async function getOperationalPoints(
    mapTile: MapTile,
    changeTime: TimeStamp,
): Promise<OperationalPoint[]> {
    return operationalPointsCache.get(changeTime, mapTile.id, () => {
        const params = queryParams({ bbox: bboxString(mapTile.area) });
        return getNonNull<OperationalPoint[]>(`${API_URI}/ratko/operational-points${params}`);
    });
}
