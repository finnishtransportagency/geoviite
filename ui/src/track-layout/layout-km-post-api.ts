import { asyncCache } from 'cache/cache';
import {
    LayoutKmLengthDetails,
    LayoutKmPost,
    LayoutKmPostId,
    LayoutTrackNumberId,
} from 'track-layout/track-layout-model';
import {
    LayoutAssetChangeInfo,
    draftLayoutContext,
    KmNumber,
    LayoutContext,
    TimeStamp,
} from 'common/common-model';
import {
    deleteNonNullAdt,
    getNonNull,
    getNullable,
    postNonNullAdt,
    putNonNullAdt,
    queryParams,
} from 'api/api-fetch';
import { changeTimeUri, layoutUri, TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';
import { getChangeTimes, updateKmPostChangeTime } from 'common/change-time-api';
import { BoundingBox, Point } from 'model/geometry';
import { bboxString, pointString } from 'common/common-api';
import { KmPostSaveError, KmPostSaveRequest } from 'linking/linking-model';
import { Result } from 'neverthrow';
import { ValidatedKmPost } from 'publication/publication-model';
import { filterNotEmpty, indexIntoMap } from 'utils/array-utils';
import i18next from 'i18next';

const kmPostListCache = asyncCache<string, LayoutKmPost[]>();
const kmPostForLinkingCache = asyncCache<string, LayoutKmPost[]>();
const kmPostCache = asyncCache<string, LayoutKmPost | undefined>();

const cacheKey = (id: LayoutKmPostId, layoutContext: LayoutContext) =>
    `${id}_${layoutContext.publicationState}_${layoutContext.designId}`;

export async function getKmPost(
    id: LayoutKmPostId,
    layoutContext: LayoutContext,
    changeTime: TimeStamp = getChangeTimes().layoutKmPost,
): Promise<LayoutKmPost | undefined> {
    return kmPostCache.get(changeTime, cacheKey(id, layoutContext), () =>
        getNullable<LayoutKmPost>(layoutUri('km-posts', layoutContext, id)),
    );
}

export async function getKmPosts(
    ids: LayoutKmPostId[],
    layoutContext: LayoutContext,
    changeTime: TimeStamp = getChangeTimes().layoutKmPost,
): Promise<LayoutKmPost[]> {
    return kmPostCache
        .getMany(
            changeTime,
            ids,
            (id) => cacheKey(id, layoutContext),
            (fetchIds) =>
                getNonNull<LayoutKmPost[]>(
                    `${layoutUri('km-posts', layoutContext)}?ids=${fetchIds}`,
                ).then((kmPosts) => {
                    const kmPostMap = indexIntoMap(kmPosts);
                    return (id) => kmPostMap.get(id);
                }),
        )
        .then((kmPosts) => kmPosts.filter(filterNotEmpty));
}

export async function getKmPostByNumber(
    layoutContext: LayoutContext,
    trackNumberId: LayoutTrackNumberId,
    kmNumber: KmNumber,
    includeDeleted: boolean,
): Promise<LayoutKmPost | undefined> {
    const params = queryParams({
        trackNumberId: trackNumberId,
        kmNumber: kmNumber,
        includeDeleted: includeDeleted,
    });
    return getNullable<LayoutKmPost>(`${layoutUri('km-posts', layoutContext)}${params}`);
}

export async function getKmPostsByTile(
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
    bbox: BoundingBox,
    step: number,
): Promise<LayoutKmPost[]> {
    const params = queryParams({
        bbox: bboxString(bbox),
        step: Math.ceil(step),
    });
    return kmPostListCache.get(
        changeTime,
        `${layoutContext.publicationState}_${layoutContext.designId}_${JSON.stringify(params)}`,
        () => getNonNull(`${layoutUri('km-posts', layoutContext)}${params}`),
    );
}

export async function getKmPostForLinking(
    layoutContext: LayoutContext,
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
        getNonNull(`${layoutUri('km-posts', layoutContext)}${params}`),
    );
}

export async function insertKmPost(
    layoutContext: LayoutContext,
    kmPost: KmPostSaveRequest,
): Promise<Result<LayoutKmPostId, KmPostSaveError>> {
    const apiResult = await postNonNullAdt<KmPostSaveRequest, LayoutKmPostId>(
        layoutUri('km-posts', draftLayoutContext(layoutContext)),
        kmPost,
    );

    await updateKmPostChangeTime();

    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function updateKmPost(
    layoutContext: LayoutContext,
    id: LayoutKmPostId,
    kmPost: KmPostSaveRequest,
): Promise<Result<LayoutKmPostId, KmPostSaveError>> {
    const apiResult = await putNonNullAdt<KmPostSaveRequest, LayoutKmPostId>(
        layoutUri('km-posts', draftLayoutContext(layoutContext), id),
        kmPost,
    );

    await updateKmPostChangeTime();

    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export const deleteDraftKmPost = async (
    layoutContext: LayoutContext,
    id: LayoutKmPostId,
): Promise<Result<LayoutKmPostId, KmPostSaveError>> => {
    const apiResult = await deleteNonNullAdt<undefined, LayoutKmPostId>(
        layoutUri('km-posts', draftLayoutContext(layoutContext), id),
        undefined,
    );

    await updateKmPostChangeTime();

    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
};

export async function getKmPostValidation(
    layoutContext: LayoutContext,
    id: LayoutKmPostId,
): Promise<ValidatedKmPost | undefined> {
    return getNullable<ValidatedKmPost>(`${layoutUri('km-posts', layoutContext, id)}/validation`);
}

export async function getKmPostsOnTrackNumber(
    layoutContext: LayoutContext,
    id: LayoutTrackNumberId,
): Promise<LayoutKmPost[]> {
    return getNonNull<LayoutKmPost[]>(
        `${layoutUri('km-posts', layoutContext)}/on-track-number/${id}`,
    );
}

export async function getKmLengths(
    layoutContext: LayoutContext,
    id: LayoutTrackNumberId,
): Promise<LayoutKmLengthDetails[]> {
    return getNonNull<LayoutKmLengthDetails[]>(
        `${layoutUri('track-numbers', layoutContext, id)}/km-lengths`,
    );
}

export async function getSingleKmPostKmLength(
    layoutContext: LayoutContext,
    id: LayoutKmPostId,
): Promise<number | undefined> {
    return getNullable<number>(`${layoutUri('km-posts', layoutContext, id)}/km-length`);
}

export const getKmLengthsAsCsv = (
    layoutContext: LayoutContext,
    trackNumberId: LayoutTrackNumberId,
    startKmNumber: KmNumber | undefined,
    endKmNumber: KmNumber | undefined,
) => {
    const params = queryParams({
        startKmNumber,
        endKmNumber,
        lang: i18next.language,
    });

    return `${layoutUri('track-numbers', layoutContext, trackNumberId)}/km-lengths/as-csv${params}`;
};

export const getKmPostChangeTimes = (id: LayoutKmPostId, layoutContext: LayoutContext) =>
    getNullable<LayoutAssetChangeInfo>(changeTimeUri('km-posts', id, layoutContext));

export const getEntireRailNetworkKmLengthsCsvUrl = () =>
    `${TRACK_LAYOUT_URI}/track-numbers/rail-network/km-lengths/file`;
