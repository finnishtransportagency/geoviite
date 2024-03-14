import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { Button } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { getEntireRailNetworkKmLengthsCsvUrl } from 'track-layout/layout-km-post-api';
import { PrivilegeRequired } from 'user/privilege-required';
import { PRIV_DOWNLOAD_GEOMETRY } from 'user/user-model';

export const EntireRailNetworkKmLengthsListing = () => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.km-lengths.legend')}
            </p>
            <PrivilegeRequired privilege={PRIV_DOWNLOAD_GEOMETRY}>
                <div className={styles['data-products__search']}>
                    <a qa-id="km-lengths-csv-download" href={getEntireRailNetworkKmLengthsCsvUrl()}>
                        <Button
                            className={
                                styles['vertical-geometry-list__download-button--left-aligned']
                            }
                            icon={Icons.Download}>
                            {t(`data-products.search.download-csv`)}
                        </Button>
                    </a>
                </div>
            </PrivilegeRequired>
        </React.Fragment>
    );
};
