import { API_URI, getAdt, getIgnoreError, postAdt } from 'api/api-fetch';
import { PublicationId } from 'publication/publication-model';
import { RatkoPushError } from 'ratko/ratko-model';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { KmNumber } from 'common/common-model';

const RATKO_URI = `${API_URI}/ratko`;

export const pushToRatko = () => getAdt(`${RATKO_URI}/push`);

export const getRatkoPushError = (publishId: PublicationId) =>
    getIgnoreError<RatkoPushError>(`${RATKO_URI}/errors/${publishId}`);

export type RatkoStatus = {
    statusCode: number;
    isOnline: boolean;
};
export const getRatkoStatus = () =>
    getAdt(`${RATKO_URI}/is-online`).then((result) => {
        if (result.isOk()) {
            return result.value;
        } else {
            return {
                statusCode: result.error.status,
                isOnline: false,
            };
        }
    });

type LocationTrackChange = {
    locationTrackId: LocationTrackId;
    changedKmNumbers: KmNumber[];
};

export function pushLocationTracksToRatko(locationTrackChanges: LocationTrackChange[]) {
    return postAdt(`${RATKO_URI}/push-location-tracks`, locationTrackChanges, true);
}
