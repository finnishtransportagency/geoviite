import { OperatingPoint } from 'track-layout/track-layout-model';
import { getNonNull, queryParams } from 'api/api-fetch';
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
        const params = queryParams({ boundingBox: bboxString(mapTile.area) });
        return getNonNull<OperatingPoint[]>(`api/ratko/operating-points${params}`);
    });
}
