import { asyncCache } from 'cache/cache';
import { LayoutKmPost, LayoutKmPostId, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { KmNumber, PublishType, TimeStamp } from 'common/common-model';
import {
    deleteAdt,
    getIgnoreError,
    getThrowError,
    postAdt,
    putAdt,
    queryParams,
} from 'api/api-fetch';
import { layoutUri } from 'track-layout/track-layout-api';
import { getChangeTimes, updateKmPostChangeTime } from 'common/change-time-api';
import { BoundingBox, Point } from 'model/geometry';
import { bboxString, pointString } from 'common/common-api';
import { KmPostSaveError, KmPostSaveRequest } from 'linking/linking-model';
import { Result } from 'neverthrow';

const kmPostListCache = asyncCache<string, LayoutKmPost[]>();
const kmPostForLinkingCache = asyncCache<string, LayoutKmPost[]>();
const kmPostCache = asyncCache<string, LayoutKmPost | null>();

export async function getKmPost(
    id: LayoutKmPostId,
    publishType: PublishType,
    changeTime: TimeStamp = getChangeTimes().layoutKmPost,
): Promise<LayoutKmPost | null> {
    const cacheKey = `${id}_${publishType}`;
    return kmPostCache.get(changeTime, cacheKey, () =>
        getIgnoreError<LayoutKmPost>(layoutUri('km-posts', publishType, id)),
    );
}

export async function getKmPostByNumber(
    publishType: PublishType,
    trackNumberId: LayoutTrackNumberId,
    kmNumber: KmNumber,
): Promise<LayoutKmPost | null> {
    const params = queryParams({
        trackNumberId: trackNumberId,
        kmNumber: kmNumber,
    });
    return getIgnoreError<LayoutKmPost>(`${layoutUri('km-posts', publishType)}${params}`);
}

export async function getKmPostsByTile(
    publishType: PublishType,
    changeTime: TimeStamp,
    bbox: BoundingBox,
    step: number,
): Promise<LayoutKmPost[]> {
    const params = queryParams({
        bbox: bboxString(bbox),
        step: Math.ceil(step),
        publishType,
    });
    return kmPostListCache.get(changeTime, `${publishType}_${JSON.stringify(params)}`, () =>
        getThrowError(`${layoutUri('km-posts', publishType)}${params}`),
    );
}

export async function getKmPostForLinking(
    publishType: PublishType,
    trackNumberId: LayoutTrackNumberId,
    location: Point,
    offset = 0,
    limit = 20,
): Promise<LayoutKmPost[]> {
    const kmPostChangeTime = getChangeTimes().layoutKmPost;
    const params = queryParams({
        trackNumberId: trackNumberId,
        location: pointString(location),
        offset: offset,
        limit: limit,
    });
    return kmPostForLinkingCache.get(kmPostChangeTime, params, () =>
        getThrowError(`${layoutUri('km-posts', publishType)}${params}`),
    );
}

export async function insertKmPost(
    kmPost: KmPostSaveRequest,
): Promise<Result<LayoutKmPostId, KmPostSaveError>> {
    const apiResult = await postAdt<KmPostSaveRequest, LayoutKmPostId>(
        layoutUri('km-posts', 'DRAFT'),
        kmPost,
        true,
    );
    updateKmPostChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function updateKmPost(
    id: LayoutKmPostId,
    kmPost: KmPostSaveRequest,
): Promise<Result<LayoutKmPostId, KmPostSaveError>> {
    const apiResult = await putAdt<KmPostSaveRequest, LayoutKmPostId>(
        layoutUri('km-posts', 'DRAFT', id),
        kmPost,
        true,
    );
    updateKmPostChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export const deleteDraftKmPost = async (
    id: LayoutKmPostId,
): Promise<Result<LayoutKmPostId, KmPostSaveError>> => {
    const apiResult = await deleteAdt<undefined, LayoutKmPostId>(
        layoutUri('km-posts', 'DRAFT', id),
        undefined,
        true,
    );
    updateKmPostChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
};
