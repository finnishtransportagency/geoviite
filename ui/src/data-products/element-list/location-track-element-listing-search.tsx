import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import {
    ContinuousSearchParameters,
    ElementListContinuousGeometrySearchState,
    selectedElementTypes,
    validTrackMeterOrUndefined,
} from 'data-products/data-products-store';
import { debounceAsync } from 'utils/async-utils';
import { PropEdit } from 'utils/validation-utils';
import { useLoader } from 'utils/react-utils';
import { getLocationTrackElements, getLocationTrackElementsCsv } from 'geometry/geometry-api';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { ElementItem } from 'geometry/geometry-model';
import {
    debouncedSearchTracks,
    getLocationTrackOptions,
    getVisibleErrorsByProp,
    hasErrors,
} from 'data-products/data-products-utils';

type LocationTrackElementListingSearchProps = {
    state: ElementListContinuousGeometrySearchState;
    setElements: (elements: ElementItem[]) => void;
    onUpdateProp: <TKey extends keyof ContinuousSearchParameters>(
        propEdit: PropEdit<ContinuousSearchParameters, TKey>,
    ) => void;
    onCommitField: <TKey extends keyof ContinuousSearchParameters>(key: TKey) => void;
};

const debouncedTrackElementsFetch = debounceAsync(getLocationTrackElements, 250);

const LocationTrackElementListingSearch = ({
    state,
    onUpdateProp,
    onCommitField,
    setElements,
}: LocationTrackElementListingSearchProps) => {
    const { t } = useTranslation();

    // Use memoized function to make debouncing functionality work when re-rendering
    const getLocationTracks = React.useCallback(
        (searchTerm) =>
            debouncedSearchTracks(searchTerm, 'OFFICIAL', 10).then((locationTracks) =>
                getLocationTrackOptions(locationTracks, state.searchParameters.locationTrack),
            ),
        [state.searchParameters.locationTrack],
    );

    function updateProp<TKey extends keyof ContinuousSearchParameters>(
        key: TKey,
        value: ContinuousSearchParameters[TKey],
    ) {
        onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: false,
        });
    }

    const elementList = useLoader(() => {
        return !state.searchParameters.locationTrack ||
            hasErrors(state.committedFields, state.validationErrors, 'searchGeometries') ||
            hasErrors(state.committedFields, state.validationErrors, 'startTrackMeter') ||
            hasErrors(state.committedFields, state.validationErrors, 'endTrackMeter')
            ? Promise.resolve(state.elements)
            : debouncedTrackElementsFetch(
                  state.searchParameters.locationTrack.id,
                  selectedElementTypes(state.searchParameters.searchGeometries),
                  validTrackMeterOrUndefined(state.searchParameters.startTrackMeter),
                  validTrackMeterOrUndefined(state.searchParameters.endTrackMeter),
              );
    }, [state.searchParameters]);

    React.useEffect(() => setElements(elementList ?? []), [elementList]);

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.element-list.location-track-legend')}
            </p>
            <div className={styles['data-products__search']}>
                <FieldLayout
                    label={t('data-products.search.location-track')}
                    value={
                        <Dropdown
                            value={state.searchParameters.locationTrack}
                            getName={(item) => item.name}
                            placeholder={t('data-products.search.search')}
                            options={getLocationTracks}
                            searchable
                            onChange={(e) => updateProp('locationTrack', e)}
                            onBlur={() => onCommitField('locationTrack')}
                            unselectText={t('data-products.search.not-selected')}
                            wideList
                            wide
                        />
                    }
                />
                <FieldLayout
                    label={t('data-products.search.track-address-start')}
                    value={
                        <TextField
                            value={state.searchFields.startTrackMeter}
                            onChange={(e) => updateProp('startTrackMeter', e.target.value)}
                            onBlur={() => onCommitField('startTrackMeter')}
                            hasError={hasErrors(
                                state.committedFields,
                                state.validationErrors,
                                'startTrackMeter',
                            )}
                            wide
                        />
                    }
                    errors={getVisibleErrorsByProp(
                        state.committedFields,
                        state.validationErrors,
                        'startTrackMeter',
                    ).map((error) => t(`data-products.search.${error}`))}
                />
                <FieldLayout
                    label={t('data-products.search.track-address-end')}
                    value={
                        <TextField
                            value={state.searchFields.endTrackMeter}
                            onChange={(e) => updateProp('endTrackMeter', e.target.value)}
                            onBlur={() => onCommitField('endTrackMeter')}
                            hasError={hasErrors(
                                state.committedFields,
                                state.validationErrors,
                                'endTrackMeter',
                            )}
                            wide
                        />
                    }
                    errors={getVisibleErrorsByProp(
                        state.committedFields,
                        state.validationErrors,
                        'endTrackMeter',
                    ).map((error) => t(`data-products.search.${error}`))}
                />
                <div className={styles['data-products__search--no-label']}>
                    <FieldLayout
                        label={''}
                        value={
                            <div className={styles['element-list__geometry-checkbox']}>
                                <Checkbox
                                    checked={state.searchFields.searchGeometries.searchLines}
                                    onChange={(e) =>
                                        updateProp('searchGeometries', {
                                            ...state.searchFields.searchGeometries,
                                            searchLines: e.target.checked,
                                        })
                                    }>
                                    {t('data-products.search.line')}
                                </Checkbox>
                                <Checkbox
                                    checked={state.searchFields.searchGeometries.searchCurves}
                                    onChange={(e) =>
                                        updateProp('searchGeometries', {
                                            ...state.searchFields.searchGeometries,
                                            searchCurves: e.target.checked,
                                        })
                                    }>
                                    {t('data-products.search.curve')}
                                </Checkbox>
                                <Checkbox
                                    checked={state.searchFields.searchGeometries.searchClothoids}
                                    onChange={(e) =>
                                        updateProp('searchGeometries', {
                                            ...state.searchFields.searchGeometries,
                                            searchClothoids: e.target.checked,
                                        })
                                    }>
                                    {t('data-products.search.clothoid')}
                                </Checkbox>
                                <Checkbox
                                    checked={
                                        state.searchFields.searchGeometries.searchMissingGeometry
                                    }
                                    onChange={(e) =>
                                        updateProp('searchGeometries', {
                                            ...state.searchFields.searchGeometries,
                                            searchMissingGeometry: e.target.checked,
                                        })
                                    }>
                                    {t('data-products.search.missing-section')}
                                </Checkbox>
                            </div>
                        }
                        errors={getVisibleErrorsByProp(
                            state.committedFields,
                            state.validationErrors,
                            'searchGeometries',
                        ).map((error) => t(`data-products.search.${error}`))}
                    />
                </div>
                <Button
                    className={styles['element-list__download-button']}
                    disabled={!state.elements || state.elements.length === 0}
                    onClick={() => {
                        if (state.searchParameters.locationTrack) {
                            location.href = getLocationTrackElementsCsv(
                                state.searchParameters.locationTrack?.id,
                                selectedElementTypes(state.searchParameters.searchGeometries),
                                validTrackMeterOrUndefined(state.searchParameters.startTrackMeter),
                                validTrackMeterOrUndefined(state.searchParameters.endTrackMeter),
                            );
                        }
                    }}
                    icon={Icons.Download}>
                    {t(`data-products.search.download-csv`)}
                </Button>
            </div>
        </React.Fragment>
    );
};

export default LocationTrackElementListingSearch;
