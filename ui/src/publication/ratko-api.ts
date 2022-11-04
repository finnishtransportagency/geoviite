import { API_URI, getAdt, getIgnoreError } from 'api/api-fetch';
import { PublicationId, RatkoPushError } from 'publication/publication-model';

const RATKO_URI = `${API_URI}/ratko`;

export const pushToRatko = () => getAdt(`${RATKO_URI}/push`);

export const getRatkoPushError = (publishId: PublicationId) =>
    getIgnoreError<RatkoPushError>(`${RATKO_URI}/errors/${publishId}`);
