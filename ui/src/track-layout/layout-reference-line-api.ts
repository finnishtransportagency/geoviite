import {
    AlignmentStartAndEnd,
    LayoutReferenceLine,
    LayoutTrackNumberId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import {
    DesignBranch,
    draftLayoutContext,
    LayoutAssetChangeInfo,
    LayoutContext,
    TimeStamp,
} from 'common/common-model';
import { getNonNull, getNullable, postNonNull, queryParams } from 'api/api-fetch';
import { changeInfoUri, layoutUri, layoutUriByBranch } from 'track-layout/track-layout-api';
import { BoundingBox } from 'model/geometry';
import { bboxString } from 'common/common-api';
import { asyncCache } from 'cache/cache';
import { getChangeTimes } from 'common/change-time-api';
import { filterNotEmpty, indexIntoMap } from 'utils/array-utils';

const referenceLineCache = asyncCache<string, LayoutReferenceLine | undefined>();

export function cacheKey(id: ReferenceLineId, layoutContext: LayoutContext) {
    return `${id}_${layoutContext.publicationState}_${layoutContext.branch}`;
}

export async function getReferenceLine(
    id: ReferenceLineId,
    layoutContext: LayoutContext,
    changeTime: TimeStamp = getChangeTimes().layoutReferenceLine,
): Promise<LayoutReferenceLine | undefined> {
    return referenceLineCache.get(changeTime, cacheKey(id, layoutContext), () =>
        getNullable<LayoutReferenceLine>(layoutUri('reference-lines', layoutContext, id)),
    );
}

export async function getReferenceLines(
    ids: ReferenceLineId[],
    layoutContext: LayoutContext,
    changeTime: TimeStamp = getChangeTimes().layoutReferenceLine,
): Promise<LayoutReferenceLine[]> {
    return referenceLineCache
        .getMany(
            changeTime,
            ids,
            (id) => cacheKey(id, layoutContext),
            (fetchIds) =>
                getNonNull<LayoutReferenceLine[]>(
                    `${layoutUri('reference-lines', layoutContext)}?ids=${fetchIds}`,
                ).then((tracks) => {
                    const trackMap = indexIntoMap(tracks);
                    return (id) => trackMap.get(id);
                }),
        )
        .then((tracks) => tracks.filter(filterNotEmpty));
}

export async function getTrackNumberReferenceLine(
    trackNumberId: LayoutTrackNumberId,
    layoutContext: LayoutContext,
    changeTime: TimeStamp = getChangeTimes().layoutReferenceLine,
): Promise<LayoutReferenceLine | undefined> {
    const cacheKey = `TN_${trackNumberId}_${layoutContext.publicationState}_${layoutContext.branch}`;
    return referenceLineCache.get(changeTime, cacheKey, () =>
        getNullable<LayoutReferenceLine>(
            `${layoutUri('reference-lines', layoutContext)}/by-track-number/${trackNumberId}`,
        ),
    );
}

export async function getReferenceLineStartAndEnd(
    referenceLineId: ReferenceLineId,
    layoutContext: LayoutContext,
): Promise<AlignmentStartAndEnd | undefined> {
    return getNullable<AlignmentStartAndEnd>(
        `${layoutUri('reference-lines', layoutContext, referenceLineId)}/start-and-end`,
    );
}

export async function getReferenceLinesNear(
    layoutContext: LayoutContext,
    bbox: BoundingBox,
): Promise<LayoutReferenceLine[]> {
    const params = queryParams({ bbox: bboxString(bbox) });
    return getNonNull<LayoutReferenceLine[]>(
        `${layoutUri('reference-lines', layoutContext)}${params}`,
    );
}

export async function getNonLinkedReferenceLines(
    layoutContext: LayoutContext,
): Promise<LayoutReferenceLine[]> {
    return getNonNull<LayoutReferenceLine[]>(
        `${layoutUri('reference-lines', draftLayoutContext(layoutContext))}/non-linked`,
    );
}

export const getReferenceLineChangeTimes = (
    id: ReferenceLineId,
    layoutContext: LayoutContext,
): Promise<LayoutAssetChangeInfo | undefined> => {
    return getNullable<LayoutAssetChangeInfo>(changeInfoUri('reference-lines', id, layoutContext));
};

export async function cancelReferenceLine(
    design: DesignBranch,
    id: ReferenceLineId,
): Promise<void> {
    return postNonNull(`${layoutUriByBranch('reference-lines', design)}/${id}/cancel`, '');
}
