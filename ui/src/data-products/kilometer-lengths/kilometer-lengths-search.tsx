import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown, DropdownSize } from 'vayla-design-lib/dropdown/dropdown';
import { KmLengthsSearchState } from 'data-products/data-products-store';
import { PropEdit } from 'utils/validation-utils';
import { useLoader } from 'utils/react-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { LayoutKmPost, LayoutTrackNumber } from 'track-layout/track-layout-model';
import { getKmLengthsCsv, getKmPostsOnTrackNumber } from 'track-layout/layout-km-post-api';
import { getVisibleErrorsByProp } from 'data-products/data-products-utils';

type KilometerLengthsSearchProps = {
    state: KmLengthsSearchState;
    onUpdateProp: <TKey extends keyof KmLengthsSearchState>(
        propEdit: PropEdit<KmLengthsSearchState, TKey>,
    ) => void;
    setLengths: (lengths: never[]) => void;
};

export const KilometerLengthsSearch: React.FC<KilometerLengthsSearchProps> = ({
    state,
    onUpdateProp,
    setLengths,
}) => {
    const { t } = useTranslation();
    const geometryPlanHeaders =
        useTrackNumbers('OFFICIAL')?.map((tn) => ({ name: tn.number, value: tn })) || [];

    const kmPosts =
        useLoader(
            () => state.trackNumber && getKmPostsOnTrackNumber('OFFICIAL', state.trackNumber.id),
            [state.trackNumber],
        )?.map((km) => ({
            name: km.kmNumber,
            value: km,
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

    const kmLengths = useLoader(() => {
        return !state.trackNumber ? Promise.resolve([]) : Promise.resolve([]);
    }, [state.trackNumber]);
    React.useEffect(() => setLengths([]), [kmLengths]);

    return (
        <React.Fragment>
            <div className={styles['data-products__search']}>
                <FieldLayout
                    label={t(`data-products.search.track-number`)}
                    value={
                        <Dropdown
                            value={state.trackNumber}
                            getName={(item: LayoutTrackNumber) => item.number}
                            placeholder={t('location-track-dialog.search')}
                            options={geometryPlanHeaders}
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
                                getName={(item: LayoutKmPost) => item.kmNumber}
                                placeholder={t('location-track-dialog.search')}
                                options={kmPosts}
                                searchable
                                onChange={(e) => updateProp('startKm', e)}
                                canUnselect={true}
                                wideList
                                size={DropdownSize.SMALL}
                            />
                            <Dropdown
                                value={state.endKm}
                                getName={(item: LayoutKmPost) => item.kmNumber}
                                placeholder={t('location-track-dialog.search')}
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
                            location.href = getKmLengthsCsv(state.trackNumber?.id);
                        }
                    }}
                    icon={Icons.Download}>
                    {t(`data-products.search.download-csv`)}
                </Button>
            </div>
        </React.Fragment>
    );
};

export default KilometerLengthsSearch;
