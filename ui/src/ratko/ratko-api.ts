import { API_URI, getNonNull, getNonNullAdt, postAdt, postIgnoreError } from 'api/api-fetch';
import { PublicationId } from 'publication/publication-model';
import { RatkoPushError } from 'ratko/ratko-model';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { KmNumber } from 'common/common-model';

const RATKO_URI = `${API_URI}/ratko`;

export const pushToRatko = () => postIgnoreError(`${RATKO_URI}/push`, undefined);

export const getRatkoPushError = (publishId: PublicationId) =>
    getNonNull<RatkoPushError>(`${RATKO_URI}/errors/${publishId}`);

export type RatkoStatus =
    | { isOnline: true }
    | {
          isOnline: false;
          statusCode: number;
      };

export const getRatkoStatus: () => Promise<RatkoStatus> = () =>
    getNonNullAdt<RatkoStatus>(`${RATKO_URI}/is-online`).then((result) => {
        if (result.isOk()) {
            return { isOnline: true };
        } else {
            return { statusCode: result.error.status, isOnline: false };
        }
    });

type LocationTrackChange = {
    locationTrackId: LocationTrackId;
    changedKmNumbers: KmNumber[];
};

export function pushLocationTracksToRatko(locationTrackChanges: LocationTrackChange[]) {
    return postAdt(`${RATKO_URI}/push-location-tracks`, locationTrackChanges, true);
}
