import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { getEntireRailNetworkElementsCsvUrl } from 'geometry/geometry-api';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { PrivilegeRequired } from 'user/privilege-required';

export const EntireRailNetworkElementListing = () => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.element-list.entire-rail-network-legend')}
            </p>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.element-list.entire-rail-network-length-warning')}
            </p>
            <PrivilegeRequired privilege="dataproduct-download">
                <div className={styles['data-products__search']}>
                    <a
                        qa-id={'element-list-csv-download'}
                        href={getEntireRailNetworkElementsCsvUrl()}>
                        <Button
                            className={`${styles['element-list__download-button--left-aligned']} ${styles['element-list__download-button']}`}
                            icon={Icons.Download}>
                            {t(`data-products.search.download-csv`)}
                        </Button>
                    </a>
                </div>
            </PrivilegeRequired>
        </React.Fragment>
    );
};
