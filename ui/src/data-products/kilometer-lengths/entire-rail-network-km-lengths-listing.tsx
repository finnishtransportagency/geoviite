import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { Button } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { getEntireRailNetworkKmLengthsCsvUrl } from 'track-layout/layout-km-post-api';

export const EntireRailNetworkKmLengthsListing = () => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.km-lengths.legend')}
            </p>
            <div className={styles['data-products__search']}>
                <Button
                    className={styles['vertical-geometry-list__download-button--left-aligned']}
                    onClick={() => (location.href = getEntireRailNetworkKmLengthsCsvUrl())}
                    icon={Icons.Download}>
                    {t(`data-products.search.download-csv`)}
                </Button>
            </div>
        </React.Fragment>
    );
};