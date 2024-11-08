import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown, DropdownSize } from 'vayla-design-lib/dropdown/dropdown';
import { PropEdit } from 'utils/validation-utils';
import { LoaderStatus, useLoader, useLoaderWithStatus } from 'utils/react-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { LayoutKmLengthDetails, LayoutTrackNumber } from 'track-layout/track-layout-model';
import {
    getKmLengths,
    getKmLengthsAsCsv,
    getKmPostsOnTrackNumber,
} from 'track-layout/layout-km-post-api';
import { getVisibleErrorsByProp } from 'data-products/data-products-utils';
import {
    KmLengthsLocationPrecision,
    KmLengthsSearchState,
} from 'data-products/data-products-slice';
import { PrivilegeRequired } from 'user/privilege-required';
import { DOWNLOAD_GEOMETRY } from 'user/user-model';
import { officialMainLayoutContext } from 'common/common-model';
import { Radio } from 'vayla-design-lib/radio/radio';

type KilometerLengthsSearchProps = {
    state: KmLengthsSearchState;
    onUpdateProp: <TKey extends keyof KmLengthsSearchState>(
        propEdit: PropEdit<KmLengthsSearchState, TKey>,
    ) => void;
    setLengths: (lengths: LayoutKmLengthDetails[]) => void;
    setLoading: (isLoading: boolean) => void;
    locationPrecision: KmLengthsLocationPrecision;
    setLocationPrecision: (precision: KmLengthsLocationPrecision) => void;
};

export const KilometerLengthsSearch: React.FC<KilometerLengthsSearchProps> = ({
    state,
    onUpdateProp,
    setLengths,
    setLoading,
    locationPrecision,
    setLocationPrecision,
}) => {
    const { t } = useTranslation();
    const trackNumbers =
        useTrackNumbers(officialMainLayoutContext())?.map((tn) => ({
            name: tn.number,
            value: tn,
            qaId: `track-number-${tn.number}`,
        })) || [];

    const kmPosts =
        useLoader(
            () =>
                state.trackNumber &&
                getKmPostsOnTrackNumber(officialMainLayoutContext(), state.trackNumber.id),
            [state.trackNumber],
        )?.map((km) => ({
            name: km.kmNumber,
            value: km.kmNumber,
            qaId: `km-${km.kmNumber}`,
        })) || [];

    function updateProp<TKey extends keyof KmLengthsSearchState>(
        key: TKey,
        value: KmLengthsSearchState[TKey],
    ) {
        onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: false,
        });
    }

    const [kmLengths, fetchStatus] = useLoaderWithStatus(
        () =>
            state.trackNumber
                ? getKmLengths(officialMainLayoutContext(), state.trackNumber.id)
                : Promise.resolve([]),
        [state.trackNumber],
    );
    React.useEffect(() => {
        setLengths(kmLengths ?? []);
    }, [kmLengths]);
    React.useEffect(() => {
        setLoading(fetchStatus !== LoaderStatus.Ready);
    }, [fetchStatus]);

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.km-lengths.legend')}
            </p>
            <div className={styles['data-products__search']}>
                <FieldLayout
                    label={t(`data-products.search.track-number`)}
                    value={
                        <Dropdown
                            qaId="km-lengths-search-location-track"
                            value={state.trackNumber}
                            getName={(item: LayoutTrackNumber) => item.number}
                            placeholder={t('data-products.search.search')}
                            options={trackNumbers}
                            searchable
                            onChange={(e) => updateProp('trackNumber', e)}
                            canUnselect={true}
                            unselectText={t('data-products.search.not-selected')}
                            wideList
                            wide
                        />
                    }
                />
                <FieldLayout
                    label={t(`data-products.search.track-meter-range`)}
                    value={
                        <span className={styles['data-products__search--combined-field']}>
                            <Dropdown
                                qaId="km-lengths-search-start-km"
                                value={state.startKm}
                                placeholder={t('data-products.search.search')}
                                options={kmPosts}
                                searchable
                                onChange={(e) => updateProp('startKm', e)}
                                canUnselect={true}
                                wideList
                                size={DropdownSize.SMALL}
                            />
                            <Dropdown
                                qaId="km-lengths-search-end-km"
                                value={state.endKm}
                                placeholder={t('data-products.search.search')}
                                options={kmPosts}
                                searchable
                                onChange={(e) => updateProp('endKm', e)}
                                canUnselect={true}
                                wideList
                                size={DropdownSize.SMALL}
                            />
                        </span>
                    }
                    errors={getVisibleErrorsByProp(
                        state.committedFields,
                        state.validationIssues,
                        'endKm',
                    ).map((error) => t(`data-products.search.${error}`))}
                />
                <FieldLayout
                    label={t(`data-products.search.location-info`)}
                    value={
                        <span className={styles['data-product-view__radio-layout']}>
                            <Radio
                                checked={locationPrecision === 'PRECISE_LOCATION'}
                                onChange={() => setLocationPrecision('PRECISE_LOCATION')}>
                                {t('data-products.search.precise-location')}
                            </Radio>
                            <Radio
                                checked={locationPrecision === 'APPROXIMATION_IN_LAYOUT'}
                                onChange={() => setLocationPrecision('APPROXIMATION_IN_LAYOUT')}>
                                {t('data-products.search.layout-location')}
                            </Radio>
                        </span>
                    }
                />
                <PrivilegeRequired privilege={DOWNLOAD_GEOMETRY}>
                    <a
                        qa-id="km-lengths-csv-download"
                        {...(state.trackNumber && {
                            href: getKmLengthsAsCsv(
                                officialMainLayoutContext(),
                                state.trackNumber?.id,
                                state.startKm,
                                state.endKm,
                                state.locationPrecision,
                            ),
                        })}>
                        <Button
                            className={styles['element-list__download-button']}
                            disabled={!state.kmLengths || state.kmLengths.length === 0}
                            icon={Icons.Download}>
                            {t(`data-products.search.download-csv`)}
                        </Button>
                    </a>
                </PrivilegeRequired>
            </div>
        </React.Fragment>
    );
};

export default KilometerLengthsSearch;
