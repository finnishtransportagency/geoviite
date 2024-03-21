import { OperatingPoint } from 'track-layout/track-layout-model';
import { getNonNull, queryParams } from 'api/api-fetch';
import { asyncCache } from 'cache/cache';
import { MapTile } from 'map/map-model';
import { bboxString } from 'common/common-api';

const operatingPointCache = asyncCache<string, OperatingPoint[]>();

export async function getOperatingPoints(mapTile: MapTile): Promise<OperatingPoint[]> {
    return operatingPointCache.get('2000-01-01 00:00:00', mapTile.id, () => {
        const params = queryParams({ boundingBox: bboxString(mapTile.area) });
        return getNonNull<OperatingPoint[]>(`api/ratko/operating-points${params}`);
    });
}
