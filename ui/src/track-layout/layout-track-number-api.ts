import { asyncCache } from 'cache/cache';
import {
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import {
    DraftableChangeInfo,
    draftLayoutContext,
    LayoutContext,
    TimeStamp,
} from 'common/common-model';
import {
    deleteNonNullAdt,
    getNonNull,
    getNullable,
    postNonNull,
    putNonNull,
    queryParams,
} from 'api/api-fetch';
import { changeTimeUri, layoutUri } from 'track-layout/track-layout-api';
import { TrackNumberSaveRequest } from 'tool-panel/track-number/dialog/track-number-edit-store';
import {
    getChangeTimes,
    updateReferenceLineChangeTime,
    updateTrackNumberChangeTime,
} from 'common/change-time-api';
import { LocationTrackSaveError } from 'linking/linking-model';
import { Result } from 'neverthrow';
import { ValidatedAsset } from 'publication/publication-model';
import { AlignmentPlanSection } from 'track-layout/layout-location-track-api';
import { bboxString } from 'common/common-api';
import { BoundingBox } from 'model/geometry';

const trackNumbersCache = asyncCache<string, LayoutTrackNumber[]>();

export async function getTrackNumberById(
    trackNumberId: LayoutTrackNumberId,
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): Promise<LayoutTrackNumber | undefined> {
    return getTrackNumbers(layoutContext, changeTime, true).then((trackNumbers) =>
        trackNumbers.find((trackNumber) => trackNumber.id == trackNumberId),
    );
}

export async function getTrackNumbers(
    layoutContext: LayoutContext,
    changeTime: TimeStamp = getChangeTimes().layoutTrackNumber,
    includeDeleted = false,
): Promise<LayoutTrackNumber[]> {
    const cacheKey = `${includeDeleted}_${layoutContext.publicationState}_${layoutContext.designId}`;
    return trackNumbersCache.get(changeTime, cacheKey, () =>
        getNonNull<LayoutTrackNumber[]>(
            layoutUri('track-numbers', layoutContext) + queryParams({ includeDeleted }),
        ),
    );
}

export async function updateTrackNumber(
    layoutContext: LayoutContext,
    trackNumberId: LayoutTrackNumberId,
    request: TrackNumberSaveRequest,
): Promise<LayoutTrackNumberId | undefined> {
    const path = layoutUri('track-numbers', draftLayoutContext(layoutContext), trackNumberId);
    return await putNonNull<TrackNumberSaveRequest, LayoutTrackNumberId>(path, request).then((rs) =>
        updateTrackNumberChangeTime().then((_) => rs),
    );
}

export async function createTrackNumber(
    layoutContext: LayoutContext,
    request: TrackNumberSaveRequest,
): Promise<LayoutTrackNumberId | undefined> {
    const path = layoutUri('track-numbers', draftLayoutContext(layoutContext));
    return await postNonNull<TrackNumberSaveRequest, LayoutTrackNumberId>(path, request).then(
        (rs) => updateTrackNumberChangeTime().then((_) => rs),
    );
}

export async function deleteTrackNumber(
    layoutContext: LayoutContext,
    trackNumberId: LayoutTrackNumberId,
): Promise<Result<LocationTrackId, LocationTrackSaveError>> {
    const path = layoutUri('track-numbers', draftLayoutContext(layoutContext), trackNumberId);
    const apiResult = await deleteNonNullAdt<undefined, LayoutTrackNumberId>(path, undefined);

    await updateTrackNumberChangeTime();
    await updateReferenceLineChangeTime();

    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function getTrackNumberValidation(
    layoutContext: LayoutContext,
    id: LayoutTrackNumberId,
): Promise<ValidatedAsset | undefined> {
    return getNullable<ValidatedAsset>(
        `${layoutUri('track-numbers', layoutContext, id)}/validation`,
    );
}

export const getTrackNumberReferenceLineSectionsByPlan = async (
    layoutContext: LayoutContext,
    id: LayoutTrackNumberId,
    bbox: BoundingBox | undefined = undefined,
) => {
    const params = queryParams({ bbox: bbox ? bboxString(bbox) : undefined });
    return getNonNull<AlignmentPlanSection[]>(
        `${layoutUri('track-numbers', layoutContext, id)}/plan-geometry${params}`,
    );
};

export const getTrackNumberChangeTimes = (
    id: LayoutTrackNumberId,
    layoutContext: LayoutContext,
): Promise<DraftableChangeInfo | undefined> => {
    return getNullable<DraftableChangeInfo>(changeTimeUri('track-numbers', id, layoutContext));
};
