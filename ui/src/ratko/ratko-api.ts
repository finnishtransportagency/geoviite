import { API_URI, getAdt, getIgnoreError } from 'api/api-fetch';
import { PublicationId } from 'publication/publication-model';
import { RatkoPushError } from 'ratko/ratko-model';

const RATKO_URI = `${API_URI}/ratko`;

export const pushToRatko = () => getAdt(`${RATKO_URI}/push`);

export const getRatkoPushError = (publishId: PublicationId) =>
    getIgnoreError<RatkoPushError>(`${RATKO_URI}/errors/${publishId}`);

export type RatkoStatus = {
    statusCode: string;
    isOnline: boolean;
}
export const getRatkoStatus = () => getAdt(`${RATKO_URI}/is-online`).then(result => {

    // const value = result.isOk() && result.value;
    //            console.log('ratko status value', value)
    //
    // const err = !result.isOk()  && result.error;
    //            console.log('ratko status error', value)

    if(result.isOk()){
        console.log('ratko status value', result.value)
        return result.value
    }
    else {
        console.log('ratko status value',  {
            statusCode: result.error.status,
            isOnline: false
        })
        return {
            statusCode: result.error.status,
            isOnline: false
        }
    }

}); //result.value = RatkoStatus