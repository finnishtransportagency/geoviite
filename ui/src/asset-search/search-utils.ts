import { TFunction } from 'i18next';
import {
    LayoutKmPost,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
} from 'track-layout/track-layout-model';

export const trackNumberSearchItemName = (
    trackNumber: LayoutTrackNumber,
    t: TFunction<'translation', undefined>,
) =>
    trackNumber.state !== 'DELETED'
        ? trackNumber.number
        : t('asset-search.track-number-deleted', { trackNumber: trackNumber.number });

export const locationTrackSearchItemName = (
    locationTrack: LayoutLocationTrack,
    t: TFunction<'translation', undefined>,
) => {
    const params = { locationTrack: locationTrack.name, description: locationTrack.description };
    return locationTrack.state !== 'DELETED'
        ? t('asset-search.location-track', params)
        : t('asset-search.location-track-deleted', params);
};

export const switchSearchItemName = (sw: LayoutSwitch, t: TFunction<'translation', undefined>) =>
    sw.stateCategory !== 'NOT_EXISTING'
        ? sw.name
        : t('asset-search.switch-deleted', { switch: sw.name });

export const kmPostSearchItemName = (
    kmPost: LayoutKmPost,
    trackNumbers: LayoutTrackNumber[],
    t: TFunction<'translation', undefined>,
) => {
    const params = {
        kmPost: kmPost.kmNumber,
        trackNumber: trackNumbers.find((tn) => tn.id === kmPost.trackNumberId)?.number,
    };
    return kmPost.state !== 'DELETED'
        ? t('asset-search.km-post', params)
        : t('asset-search.km-post-deleted', params);
};
