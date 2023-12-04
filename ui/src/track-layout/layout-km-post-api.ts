import { asyncCache } from 'cache/cache';
import {
    LayoutKmLengthDetails,
    LayoutKmPost,
    LayoutKmPostId,
    LayoutTrackNumberId,
} from 'track-layout/track-layout-model';
import { DraftableChangeInfo, KmNumber, PublishType, TimeStamp } from 'common/common-model';
import { deleteAdt, getNonNull, getNullable, postAdt, putAdt, queryParams } from 'api/api-fetch';
import { changeTimeUri, layoutUri, TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';
import { getChangeTimes, updateKmPostChangeTime } from 'common/change-time-api';
import { BoundingBox, Point } from 'model/geometry';
import { bboxString, pointString } from 'common/common-api';
import { KmPostSaveError, KmPostSaveRequest } from 'linking/linking-model';
import { Result } from 'neverthrow';
import { ValidatedAsset } from 'publication/publication-model';
import { filterNotEmpty, indexIntoMap } from 'utils/array-utils';

const kmPostListCache = asyncCache<string, LayoutKmPost[]>();
const kmPostForLinkingCache = asyncCache<string, LayoutKmPost[]>();
const kmPostCache = asyncCache<string, LayoutKmPost | undefined>();

const cacheKey = (id: LayoutKmPostId, publishType: PublishType) => `${id}_${publishType}`;

export async function getKmPost(
    id: LayoutKmPostId,
    publishType: PublishType,
    changeTime: TimeStamp = getChangeTimes().layoutKmPost,
): Promise<LayoutKmPost | undefined> {
    return kmPostCache.get(changeTime, cacheKey(id, publishType), () =>
        getNullable<LayoutKmPost>(layoutUri('km-posts', publishType, id)),
    );
}

export async function getKmPosts(
    ids: LayoutKmPostId[],
    publishType: PublishType,
    changeTime: TimeStamp = getChangeTimes().layoutKmPost,
): Promise<LayoutKmPost[]> {
    return kmPostCache
        .getMany(
            changeTime,
            ids,
            (id) => cacheKey(id, publishType),
            (fetchIds) =>
                getNonNull<LayoutKmPost[]>(
                    `${layoutUri('km-posts', publishType)}?ids=${fetchIds}`,
                ).then((kmPosts) => {
                    const kmPostMap = indexIntoMap(kmPosts);
                    return (id) => kmPostMap.get(id);
                }),
        )
        .then((kmPosts) => kmPosts.filter(filterNotEmpty));
}

export async function getKmPostByNumber(
    publishType: PublishType,
    trackNumberId: LayoutTrackNumberId,
    kmNumber: KmNumber,
    includeDeleted: boolean,
): Promise<LayoutKmPost | undefined> {
    const params = queryParams({
        trackNumberId: trackNumberId,
        kmNumber: kmNumber,
        includeDeleted: includeDeleted,
    });
    return getNullable<LayoutKmPost>(`${layoutUri('km-posts', publishType)}${params}`);
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
        getNonNull(`${layoutUri('km-posts', publishType)}${params}`),
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
        getNonNull(`${layoutUri('km-posts', publishType)}${params}`),
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

export async function getKmPostValidation(
    publishType: PublishType,
    id: LayoutKmPostId,
): Promise<ValidatedAsset> {
    return getNonNull<ValidatedAsset>(`${layoutUri('km-posts', publishType, id)}/validation`);
}

export async function getKmPostsOnTrackNumber(
    publishType: PublishType,
    id: LayoutTrackNumberId,
): Promise<LayoutKmPost[]> {
    return getNonNull<LayoutKmPost[]>(
        `${layoutUri('km-posts', publishType)}/on-track-number/${id}`,
    );
}

export async function getKmLengths(
    publishType: PublishType,
    id: LayoutTrackNumberId,
): Promise<LayoutKmLengthDetails[]> {
    return getNonNull<LayoutKmLengthDetails[]>(
        `${layoutUri('track-numbers', publishType, id)}/km-lengths`,
    );
}

export async function getSingleKmPostKmLength(
    publishType: PublishType,
    id: LayoutKmPostId,
): Promise<number | undefined> {
    return getNullable<number>(`${layoutUri('km-posts', publishType, id)}/km-length`);
}

export const getKmLengthsAsCsv = (
    publishType: PublishType,
    trackNumberId: LayoutTrackNumberId,
    startKmNumber: KmNumber | undefined,
    endKmNumber: KmNumber | undefined,
) => {
    const params = queryParams({
        startKmNumber,
        endKmNumber,
    });

    return `${layoutUri('track-numbers', publishType, trackNumberId)}/km-lengths/as-csv${params}`;
};

export const getKmPostChangeTimes = (id: LayoutKmPostId, publishType: PublishType) =>
    getNullable<DraftableChangeInfo>(changeTimeUri('km-posts', id, publishType));

export const getEntireRailNetworkKmLengthsCsvUrl = () =>
    `${TRACK_LAYOUT_URI}/track-numbers/rail-network/km-lengths/file`;
