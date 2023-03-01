import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';

export const LocationTrackVerticalGeometrySearch: React.FC = () => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.vertical-geometry.location-track-search-legend')}
            </p>
        </React.Fragment>
    );
};

export default LocationTrackVerticalGeometrySearch;
