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
import { AlignmentSectionByPlan } from 'track-layout/layout-location-track-api';

const referenceLineCache = asyncCache<string, LayoutReferenceLine | null>();

export async function getReferenceLine(
    id: ReferenceLineId,
    publishType: PublishType,
    changeTime?: TimeStamp,
): Promise<LayoutReferenceLine | null> {
    const cacheKey = `${id}_${publishType}`;
    return referenceLineCache.get(
        changeTime || getChangeTimes().layoutReferenceLine,
        cacheKey,
        () => getIgnoreError<LayoutReferenceLine>(layoutUri('reference-lines', publishType, id)),
    );
}

export async function getTrackNumberReferenceLine(
    trackNumberId: LayoutTrackNumberId,
    publishType: PublishType,
    changeTime?: TimeStamp,
): Promise<LayoutReferenceLine | null> {
    const cacheKey = `TN_${trackNumberId}_${publishType}`;
    return referenceLineCache.get(
        changeTime || getChangeTimes().layoutReferenceLine,
        cacheKey,
        () =>
            getIgnoreError<LayoutReferenceLine>(
                `${layoutUri('reference-lines', publishType)}/by-track-number/${trackNumberId}`,
            ),
    );
}

export async function getReferenceLineStartAndEnd(
    referenceLineId: ReferenceLineId,
    publishType: PublishType,
): Promise<AlignmentStartAndEnd | undefined> {
    return getThrowError<AlignmentStartAndEnd>(
        `${layoutUri('reference-lines', publishType, referenceLineId)}/start-and-end`,
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

export const getReferenceLineChangeTimes = (id: ReferenceLineId): Promise<ChangeTimes | null> => {
    return getIgnoreError<ChangeTimes>(changeTimeUri('reference-lines', id));
};

export const getReferenceLineSectionsByPlan = async (
    publishType: PublishType,
    id: ReferenceLineId,
    bbox: BoundingBox | undefined = undefined,
) => {
    const params = queryParams({ bbox: bbox ? bboxString(bbox) : undefined });
    return getIgnoreError<AlignmentSectionByPlan[]>(
        `${layoutUri('reference-lines', publishType, id)}/plan-geometry/${params}`,
    );
};
