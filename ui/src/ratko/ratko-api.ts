import { API_URI, getAdt, getIgnoreError } from 'api/api-fetch';
import { PublicationId } from 'publication/publication-model';
import { RatkoPushError } from 'ratko/ratko-model';

const RATKO_URI = `${API_URI}/ratko`;

export const pushToRatko = () => getAdt(`${RATKO_URI}/push`);

export const getRatkoPushError = (publishId: PublicationId) =>
    getIgnoreError<RatkoPushError>(`${RATKO_URI}/errors/${publishId}`);

export const getRatkoStatus = () => getAdt(`${RATKO_URI}/is-online`).then(result =>

{
    //if (result.isOk() && result.value) return true ; else return false} )
    if (result.isOk()) return result.value ; else return result.error.status} ) //result.value = RatkoStatus