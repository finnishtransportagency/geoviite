import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { useDataProductsAppDispatch, useDataProductsAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { dataProductsActions } from 'data-products/data-products-store';
import KilometerLengthsSearch from 'data-products/kilometer-lengths/kilometer-lengths-search';

export const KilometerLengthsView = () => {
    const rootDispatch = useDataProductsAppDispatch();
    const dataProductsDelegates = createDelegates(rootDispatch, dataProductsActions);
    const state = useDataProductsAppSelector((state) => state.dataProducts.kmLenghts);

    const { t } = useTranslation();

    return (
        <div className={styles['data-product-view']}>
            <div className={styles['data-product-view__header-container']}>
                <h2>{t('data-products.km-lengths.title')}</h2>
                <p className={styles['data-product__search-legend']}>
                    {t('data-products.km-lengths.legend')}
                </p>
                <KilometerLengthsSearch
                    setLengths={dataProductsDelegates.onSetKmLengths}
                    state={state}
                    onUpdateProp={dataProductsDelegates.onUpdateKmLengthsSearchProp}
                />
            </div>
        </div>
    );
};
