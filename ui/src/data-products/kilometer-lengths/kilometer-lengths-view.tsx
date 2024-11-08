import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { useDataProductsAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import KilometerLengthsSearch from 'data-products/kilometer-lengths/kilometer-lengths-search';
import KilometerLengthsTable from 'data-products/kilometer-lengths/kilometer-lengths-table';
import { KmNumber } from 'common/common-model';
import { LayoutKmLengthDetails } from 'track-layout/track-layout-model';
import { dataProductsActions, SelectedKmLengthsSearch } from 'data-products/data-products-slice';
import { Radio } from 'vayla-design-lib/radio/radio';
import { EntireRailNetworkKmLengthsListing } from 'data-products/kilometer-lengths/entire-rail-network-km-lengths-listing';
import { PrivilegeRequired } from 'user/privilege-required';
import { DOWNLOAD_GEOMETRY } from 'user/user-model';

export const KilometerLengthsView = () => {
    const dataProductsDelegates = React.useMemo(() => createDelegates(dataProductsActions), []);
    const state = useDataProductsAppSelector((state) => state.kmLenghts);
    const { t } = useTranslation();
    const [loading, setLoading] = React.useState(false);

    const handleRadioClick = (selected: SelectedKmLengthsSearch) => {
        dataProductsDelegates.setSelectedKmLengthsSearch(selected);
    };

    const startIndex = state.startKm ? findIndex(state.startKm, state.kmLengths) : 0;
    const endIndex = state.endKm
        ? findIndex(state.endKm, state.kmLengths) + 1
        : state.kmLengths.length;
    const kmLengths =
        startIndex === 0 && endIndex == state.kmLengths.length
            ? state.kmLengths
            : state.kmLengths.slice(startIndex, endIndex);

    return (
        <div className={styles['data-product-view']}>
            <div className={styles['data-product-view__header-container']}>
                <h2>{t('data-products.km-lengths.title')}</h2>
                <div>
                    <span className={styles['data-product-view__radio-layout']}>
                        <Radio
                            qaId="select-location-track-km-lengths"
                            onChange={() => handleRadioClick('TRACK_NUMBER')}
                            checked={state.selectedSearch === 'TRACK_NUMBER'}>
                            {t('data-products.km-lengths.track-number-km-lengths')}
                        </Radio>
                        <PrivilegeRequired privilege={DOWNLOAD_GEOMETRY}>
                            <Radio
                                qaId="select-entire-rail-network"
                                onChange={() => handleRadioClick('ENTIRE_RAIL_NETWORK')}
                                checked={state.selectedSearch === 'ENTIRE_RAIL_NETWORK'}>
                                {t('data-products.km-lengths.entire-rail-network-km-lengths')}
                            </Radio>
                        </PrivilegeRequired>
                    </span>
                </div>
                {state.selectedSearch === 'TRACK_NUMBER' && (
                    <KilometerLengthsSearch
                        setLengths={dataProductsDelegates.onSetKmLengths}
                        state={state}
                        onUpdateProp={dataProductsDelegates.onUpdateKmLengthsSearchProp}
                        setLoading={setLoading}
                        locationPrecision={state.locationPrecision}
                        setLocationPrecision={dataProductsDelegates.setKmLengthsLocationPrecision}
                    />
                )}
                {state.selectedSearch === 'ENTIRE_RAIL_NETWORK' && (
                    <EntireRailNetworkKmLengthsListing />
                )}
            </div>
            {state.selectedSearch !== 'ENTIRE_RAIL_NETWORK' && (
                <KilometerLengthsTable
                    kmLengths={kmLengths}
                    isLoading={loading}
                    locationPrecision={state.locationPrecision}
                />
            )}
        </div>
    );
};

const findIndex = (kmNumber: KmNumber, kmLengths: LayoutKmLengthDetails[]): number => {
    return kmLengths.findIndex((km) => km.kmNumber >= kmNumber);
};
