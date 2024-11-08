import { asyncCache } from 'cache/cache';
import {
    KmPostInfoboxExtras,
    LayoutKmLengthDetails,
    LayoutKmPost,
    LayoutKmPostId,
    LayoutTrackNumberId,
} from 'track-layout/track-layout-model';
import {
    draftLayoutContext,
    KmNumber,
    LayoutAssetChangeInfo,
    LayoutContext,
    TimeStamp,
} from 'common/common-model';
import {
    deleteNonNull,
    getNonNull,
    getNullable,
    postNonNull,
    putNonNull,
    queryParams,
} from 'api/api-fetch';
import { changeInfoUri, layoutUri } from 'track-layout/track-layout-api';
import { getChangeTimes, updateKmPostChangeTime } from 'common/change-time-api';
import { BoundingBox, Point } from 'model/geometry';
import { bboxString, pointString } from 'common/common-api';
import { KmPostSaveRequest } from 'linking/linking-model';
import { ValidatedKmPost } from 'publication/publication-model';
import { filterNotEmpty, indexIntoMap } from 'utils/array-utils';
import i18next from 'i18next';
import { KmLengthsLocationPrecision } from 'data-products/data-products-slice';

const kmPostListCache = asyncCache<string, LayoutKmPost[]>();
const kmPostForLinkingCache = asyncCache<string, LayoutKmPost[]>();
const kmPostCache = asyncCache<string, LayoutKmPost | undefined>();

const cacheKey = (id: LayoutKmPostId, layoutContext: LayoutContext) =>
    `${id}_${layoutContext.publicationState}_${layoutContext.branch}`;

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
        `${layoutContext.publicationState}_${layoutContext.branch}_${JSON.stringify(params)}`,
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
    const key = `${params}_${layoutContext.branch}`;
    return kmPostForLinkingCache.get(kmPostChangeTime, key, () =>
        getNonNull(`${layoutUri('km-posts', layoutContext)}${params}`),
    );
}

export async function insertKmPost(
    layoutContext: LayoutContext,
    kmPost: KmPostSaveRequest,
): Promise<LayoutKmPostId> {
    const result = await postNonNull<KmPostSaveRequest, LayoutKmPostId>(
        layoutUri('km-posts', draftLayoutContext(layoutContext)),
        kmPost,
    );

    await updateKmPostChangeTime();

    return result;
}

export async function updateKmPost(
    layoutContext: LayoutContext,
    id: LayoutKmPostId,
    kmPost: KmPostSaveRequest,
): Promise<LayoutKmPostId> {
    const result = await putNonNull<KmPostSaveRequest, LayoutKmPostId>(
        layoutUri('km-posts', draftLayoutContext(layoutContext), id),
        kmPost,
    );

    await updateKmPostChangeTime();

    return result;
}

export const deleteDraftKmPost = async (
    layoutContext: LayoutContext,
    id: LayoutKmPostId,
): Promise<LayoutKmPostId> => {
    const result = await deleteNonNull<LayoutKmPostId>(
        layoutUri('km-posts', draftLayoutContext(layoutContext), id),
    );

    await updateKmPostChangeTime();

    return result;
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

export async function getKmPostInfoboxExtras(
    layoutContext: LayoutContext,
    id: LayoutKmPostId,
): Promise<KmPostInfoboxExtras | undefined> {
    return getNullable<KmPostInfoboxExtras>(
        `${layoutUri('km-posts', layoutContext, id)}/infobox-extras`,
    );
}

export const getKmLengthsAsCsv = (
    layoutContext: LayoutContext,
    trackNumberId: LayoutTrackNumberId,
    startKmNumber: KmNumber | undefined,
    endKmNumber: KmNumber | undefined,
    precision: KmLengthsLocationPrecision,
) => {
    const params = queryParams({
        startKmNumber,
        endKmNumber,
        precision,
        lang: i18next.language,
    });

    return `${layoutUri('track-numbers', layoutContext, trackNumberId)}/km-lengths/as-csv${params}`;
};

export const getKmPostChangeInfo = (id: LayoutKmPostId, layoutContext: LayoutContext) =>
    getNullable<LayoutAssetChangeInfo>(changeInfoUri('km-posts', id, layoutContext));

export const getEntireRailNetworkKmLengthsCsvUrl = (layoutContext: LayoutContext) =>
    `${layoutUri('track-numbers', layoutContext)}/rail-network/km-lengths/file`;
