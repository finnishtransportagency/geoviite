import { asyncCache } from 'cache/cache';
import {
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { PublishType, TimeStamp } from 'common/common-model';
import {
    deleteAdt,
    getThrowError,
    postIgnoreError,
    putIgnoreError,
    queryParams,
} from 'api/api-fetch';
import { layoutUri } from 'track-layout/track-layout-api';
import { TrackNumberSaveRequest } from 'tool-panel/track-number/dialog/track-number-edit-store';
import {
    getChangeTimes,
    updateReferenceLineChangeTime,
    updateTrackNumberChangeTime,
} from 'common/change-time-api';
import { LocationTrackSaveError } from 'linking/linking-model';
import { Result } from 'neverthrow';
import { ValidatedAsset } from 'publication/publication-model';

const trackNumbersCache = asyncCache<string, LayoutTrackNumber[]>();

export async function getTrackNumberById(
    trackNumberId: LayoutTrackNumberId,
    publishType: PublishType,
    changeTime?: TimeStamp,
): Promise<LayoutTrackNumber | undefined> {
    return getTrackNumbers(publishType, changeTime).then((trackNumbers) =>
        trackNumbers.find((trackNumber) => trackNumber.id == trackNumberId),
    );
}

export async function getTrackNumbers(
    publishType: PublishType,
    changeTime?: TimeStamp,
    includeDeleted = false,
): Promise<LayoutTrackNumber[]> {
    const cacheKey = `${includeDeleted}_${publishType}`;
    return trackNumbersCache.get(changeTime || getChangeTimes().layoutTrackNumber, cacheKey, () =>
        getThrowError<LayoutTrackNumber[]>(
            layoutUri('track-numbers', publishType) + queryParams({ includeDeleted }),
        ),
    );
}

export async function updateTrackNumber(
    trackNumberId: LayoutTrackNumberId,
    request: TrackNumberSaveRequest,
): Promise<LayoutTrackNumberId | null> {
    const path = layoutUri('track-numbers', 'DRAFT', trackNumberId);
    return await putIgnoreError<TrackNumberSaveRequest, LayoutTrackNumberId>(path, request).then(
        (rs) => updateTrackNumberChangeTime().then((_) => rs),
    );
}

export async function createTrackNumber(
    request: TrackNumberSaveRequest,
): Promise<LayoutTrackNumberId | null> {
    const path = layoutUri('track-numbers', 'DRAFT');
    return await postIgnoreError<TrackNumberSaveRequest, LayoutTrackNumberId>(path, request).then(
        (rs) => updateTrackNumberChangeTime().then((_) => rs),
    );
}

export async function deleteTrackNumber(
    trackNumberId: LayoutTrackNumberId,
): Promise<Result<LocationTrackId, LocationTrackSaveError>> {
    const path = layoutUri('track-numbers', 'DRAFT', trackNumberId);
    const apiResult = await deleteAdt<undefined, LayoutTrackNumberId>(path, undefined, true);
    updateTrackNumberChangeTime();
    updateReferenceLineChangeTime();
    return apiResult.mapErr(() => ({
        // Here it is possible to return more accurate validation errors
        validationErrors: [],
    }));
}

export async function getTrackNumberValidation(
    publishType: PublishType,
    id: LayoutTrackNumberId,
): Promise<ValidatedAsset> {
    return getThrowError<ValidatedAsset>(
        `${layoutUri('track-numbers', publishType, id)}/validation`,
    );
}
