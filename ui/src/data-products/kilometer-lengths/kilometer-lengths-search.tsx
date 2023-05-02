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
import { KmLengthsSearchState } from 'data-products/data-products-slice';

type KilometerLengthsSearchProps = {
    state: KmLengthsSearchState;
    onUpdateProp: <TKey extends keyof KmLengthsSearchState>(
        propEdit: PropEdit<KmLengthsSearchState, TKey>,
    ) => void;
    setLengths: (lengths: LayoutKmLengthDetails[]) => void;
    setLoading: (isLoading: boolean) => void;
};

export const KilometerLengthsSearch: React.FC<KilometerLengthsSearchProps> = ({
    state,
    onUpdateProp,
    setLengths,
    setLoading,
}) => {
    const { t } = useTranslation();
    const trackNumbers =
        useTrackNumbers('OFFICIAL')?.map((tn) => ({ name: tn.number, value: tn })) || [];

    const kmPosts =
        useLoader(
            () => state.trackNumber && getKmPostsOnTrackNumber('OFFICIAL', state.trackNumber.id),
            [state.trackNumber],
        )?.map((km) => ({
            name: km.kmNumber,
            value: km.kmNumber,
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
                ? getKmLengths('OFFICIAL', state.trackNumber.id)
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
        <div className={styles['data-products__search']}>
            <FieldLayout
                label={t(`data-products.search.track-number`)}
                value={
                    <Dropdown
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
                    state.validationErrors,
                    'endKm',
                ).map((error) => t(`data-products.search.${error}`))}
            />
            <Button
                className={styles['element-list__download-button']}
                disabled={!state.kmLengths || state.kmLengths.length === 0}
                onClick={() => {
                    if (state.trackNumber) {
                        location.href = getKmLengthsAsCsv(
                            'OFFICIAL',
                            state.trackNumber?.id,
                            state.startKm,
                            state.endKm,
                        );
                    }
                }}
                icon={Icons.Download}>
                {t(`data-products.search.download-csv`)}
            </Button>
        </div>
    );
};

export default KilometerLengthsSearch;
