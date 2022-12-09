import { API_URI, getAdt, getIgnoreError, postAdt } from 'api/api-fetch';
import { PublicationId, RatkoPushError } from 'publication/publication-model';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { KmNumber } from 'common/common-model';

type LocationTrackChange = {
    locationTrackId: LocationTrackId;
    changedKmNumbers: KmNumber[];
};

const RATKO_URI = `${API_URI}/ratko`;

export const pushToRatko = () => getAdt(`${RATKO_URI}/push`);

export function pushLocationTracksToRatko(locationTrackChanges: LocationTrackChange[]) {
    return postAdt(`${RATKO_URI}/push-location-tracks`, locationTrackChanges, true);
}

export const getRatkoPushError = (publishId: PublicationId) =>
    getIgnoreError<RatkoPushError>(`${RATKO_URI}/errors/${publishId}`);
