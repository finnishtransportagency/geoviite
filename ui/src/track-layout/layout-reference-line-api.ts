import {
    AlignmentStartAndEnd,
    LayoutReferenceLine,
    LayoutTrackNumberId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { ChangeTimes, PublishType, TimeStamp } from 'common/common-model';
import { getIgnoreError, getThrowError, getWithDefault, queryParams } from 'api/api-fetch';
import { changeTimeUri, layoutUri } from 'track-layout/track-layout-api';
import { BoundingBox } from 'model/geometry';
import { bboxString } from 'common/common-api';
import { asyncCache } from 'cache/cache';
import { getChangeTimes } from 'common/change-time-api';
import { filterNotEmpty, indexIntoMap } from 'utils/array-utils';

const referenceLineCache = asyncCache<string, LayoutReferenceLine | undefined>();

export function cacheKey(id: ReferenceLineId, publishType: PublishType) {
    return `${id}_${publishType}`;
}
export async function getReferenceLine(
    id: ReferenceLineId,
    publishType: PublishType,
    changeTime: TimeStamp = getChangeTimes().layoutReferenceLine,
): Promise<LayoutReferenceLine | undefined> {
    return referenceLineCache.get(changeTime, cacheKey(id, publishType), () =>
        getIgnoreError<LayoutReferenceLine>(layoutUri('reference-lines', publishType, id)),
    );
}

export async function getReferenceLines(
    ids: ReferenceLineId[],
    publishType: PublishType,
    changeTime: TimeStamp = getChangeTimes().layoutReferenceLine,
): Promise<LayoutReferenceLine[]> {
    return referenceLineCache
        .getMany(
            changeTime,
            ids,
            (id) => cacheKey(id, publishType),
            (fetchIds) =>
                getThrowError<LayoutReferenceLine[]>(
                    `${layoutUri('reference-lines', publishType)}?ids=${fetchIds}`,
                ).then((tracks) => {
                    const trackMap = indexIntoMap(tracks);
                    return (id) => trackMap.get(id);
                }),
        )
        .then((tracks) => tracks.filter(filterNotEmpty));
}

export async function getTrackNumberReferenceLine(
    trackNumberId: LayoutTrackNumberId,
    publishType: PublishType,
    changeTime: TimeStamp = getChangeTimes().layoutReferenceLine,
): Promise<LayoutReferenceLine | undefined> {
    const cacheKey = `TN_${trackNumberId}_${publishType}`;
    return referenceLineCache.get(changeTime, cacheKey, () =>
        getIgnoreError<LayoutReferenceLine>(
            `${layoutUri('reference-lines', publishType)}/by-track-number/${trackNumberId}`,
        ),
    );
}

export async function getReferenceLineStartAndEnd(
    referenceLineId: ReferenceLineId,
    publishType: PublishType,
): Promise<AlignmentStartAndEnd | undefined> {
    return getWithDefault<AlignmentStartAndEnd | undefined>(
        `${layoutUri('reference-lines', publishType, referenceLineId)}/start-and-end`,
        undefined,
    );
}

export async function getReferenceLinesNear(
    publishType: PublishType,
    bbox: BoundingBox,
): Promise<LayoutReferenceLine[]> {
    const params = queryParams({ bbox: bboxString(bbox) });
    return getThrowError<LayoutReferenceLine[]>(
        `${layoutUri('reference-lines', publishType)}${params}`,
    );
}

export async function getNonLinkedReferenceLines(): Promise<LayoutReferenceLine[]> {
    return getWithDefault<LayoutReferenceLine[]>(
        `${layoutUri('reference-lines', 'DRAFT')}/non-linked`,
        [],
    );
}

export const getReferenceLineChangeTimes = (
    id: ReferenceLineId,
): Promise<ChangeTimes | undefined> => {
    return getIgnoreError<ChangeTimes>(changeTimeUri('reference-lines', id));
};
