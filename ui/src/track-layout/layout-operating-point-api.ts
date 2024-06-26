import { OperatingPoint } from 'track-layout/track-layout-model';
import { API_URI, getNonNull, queryParams } from 'api/api-fetch';
import { asyncCache } from 'cache/cache';
import { MapTile } from 'map/map-model';
import { bboxString } from 'common/common-api';
import { TimeStamp } from 'common/common-model';

const operatingPointCache = asyncCache<string, OperatingPoint[]>();

export async function getOperatingPoints(
    mapTile: MapTile,
    changeTime: TimeStamp,
): Promise<OperatingPoint[]> {
    return operatingPointCache.get(changeTime, mapTile.id, () => {
        const params = queryParams({ bbox: bboxString(mapTile.area) });
        return getNonNull<OperatingPoint[]>(`${API_URI}/ratko/operating-points${params}`);
    });
}
