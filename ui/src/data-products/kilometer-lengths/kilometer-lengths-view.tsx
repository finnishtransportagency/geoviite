import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { useDataProductsAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import KilometerLengthsSearch from 'data-products/kilometer-lengths/kilometer-lengths-search';
import { KilometerLengthsTable } from 'data-products/kilometer-lengths/kilometer-lengths-table';
import { KmNumber } from 'common/common-model';
import { LayoutKmLengthDetails } from 'track-layout/track-layout-model';
import { dataProductsActions } from 'data-products/data-products-slice';

export const KilometerLengthsView = () => {
    const dataProductsDelegates = React.useMemo(() => createDelegates(dataProductsActions), []);
    const state = useDataProductsAppSelector((state) => state.kmLenghts);
    const { t } = useTranslation();
    const [loading, setLoading] = React.useState(false);

    const startIndex = state.startKm ? findIndex(state.startKm, state.kmLengths) : 0;
    const endIndex = state.endKm
        ? findIndex(state.endKm, state.kmLengths) + 1
        : state.kmLengths.length;
    const kmLengths = state.kmLengths.slice(startIndex, endIndex);

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
                    setLoading={setLoading}
                />
            </div>
            <KilometerLengthsTable kmLengths={kmLengths} isLoading={loading} />
        </div>
    );
};

const findIndex = (kmNumber: KmNumber, kmLengths: LayoutKmLengthDetails[]): number => {
    return kmLengths.findIndex((km) => km.kmNumber >= kmNumber);
};
