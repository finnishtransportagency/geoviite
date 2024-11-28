import { API_URI, getNonNull, getNonNullAdt, postNullable } from 'api/api-fetch';
import { LayoutBranchType, PublicationId } from 'publication/publication-model';
import { RatkoPushError } from 'ratko/ratko-model';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { KmNumber } from 'common/common-model';

const RATKO_URI = `${API_URI}/ratko`;

export const pushToRatko = (branchType: LayoutBranchType) =>
    postNullable(`${RATKO_URI}/push${branchType === 'MAIN' ? '' : '-designs'}`, undefined);

export const getRatkoPushError = (publicationId: PublicationId) =>
    getNonNull<RatkoPushError>(`${RATKO_URI}/errors/${publicationId}`);

export type RatkoStatus = { isOnline: boolean; ratkoStatusCode: number | undefined };

export const getRatkoStatus: () => Promise<RatkoStatus> = () =>
    getNonNullAdt<RatkoStatus>(`${RATKO_URI}/is-online`).then((result) => {
        if (result.isOk()) {
            return result.value;
        } else {
            return {
                ratkoStatusCode: undefined,
                isOnline: false,
            };
        }
    });

type LocationTrackChange = {
    locationTrackId: LocationTrackId;
    changedKmNumbers: KmNumber[];
};

export function pushLocationTracksToRatko(locationTrackChanges: LocationTrackChange[]) {
    return postNullable<LocationTrackChange[], undefined>(
        `${RATKO_URI}/push-location-tracks`,
        locationTrackChanges,
        false,
    );
}
