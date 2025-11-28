import * as React from 'react';
import { TFunction } from 'i18next';
import {
    LayoutKmPost,
    LayoutLocationTrack,
    LayoutTrackNumber,
    OperationalPoint,
} from 'track-layout/track-layout-model';
import styles from './search-dropdown.scss';

type SearchDropdownItemProps = {
    name: string;
    isDeleted: boolean;
    deletedPhrase: string;
};

export const SearchDropdownItem: React.FC<SearchDropdownItemProps> = ({
    name,
    isDeleted,
    deletedPhrase,
}) => (
    <div className={styles['search-dropdown-item']}>
        <div className={styles['search-dropdown-item__name']}>{name}</div>
        {isDeleted && (
            <div className={styles['search-dropdown-item__deleted']}>{deletedPhrase}</div>
        )}
    </div>
);

export const locationTrackSearchItemName = (
    locationTrack: LayoutLocationTrack,
    t: TFunction<'translation', undefined>,
) =>
    t('asset-search.location-track', {
        locationTrack: locationTrack.name,
        description: locationTrack.description,
    });

export const kmPostSearchItemName = (
    kmPost: LayoutKmPost,
    trackNumbers: LayoutTrackNumber[],
    t: TFunction<'translation', undefined>,
) =>
    t('asset-search.km-post', {
        kmPost: kmPost.kmNumber,
        trackNumber: trackNumbers.find((tn) => tn.id === kmPost.trackNumberId)?.number,
    });

export const operationalPointItemName = (
    point: OperationalPoint,
    t: TFunction<'translation', undefined>,
) =>
    !point.abbreviation
        ? point.name
        : t('asset-search.operational-point', {
              name: point.name,
              abbreviation: point.abbreviation,
          });
