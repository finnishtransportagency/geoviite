import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { Button } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { getEntireRailNetworkVerticalGeometryCsvUrl } from 'geometry/geometry-api';

export const EntireRailNetworkVerticalGeometryListing = () => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.vertical-geometry.entire-rail-network-vertical-geometry-legend')}
            </p>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.vertical-geometry.entire-rail-network-length-warning')}
            </p>
            <div className={styles['data-products__search']}>
                <Button
                    className={styles['vertical-geometry-list__download-button--left-aligned']}
                    onClick={() => (location.href = getEntireRailNetworkVerticalGeometryCsvUrl())}
                    icon={Icons.Download}>
                    {t(`data-products.search.download-csv`)}
                </Button>
            </div>
        </React.Fragment>
    );
};
