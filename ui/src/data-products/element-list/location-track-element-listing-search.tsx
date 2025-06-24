import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { debounceAsync } from 'utils/async-utils';
import { getVisibleErrorsByProp, hasErrors, PropEdit } from 'utils/validation-utils';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { getLocationTrackElements, getLocationTrackElementsCsv } from 'geometry/geometry-api';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { ElementItem } from 'geometry/geometry-model';
import { debouncedSearchTracks, getLocationTrackOptions } from 'data-products/data-products-utils';
import {
    ContinuousSearchParameters,
    ElementListContinuousGeometrySearchState,
    selectedElementTypes,
    validTrackMeterOrUndefined,
} from 'data-products/data-products-slice';
import { PrivilegeRequired } from 'user/privilege-required';
import { DOWNLOAD_GEOMETRY } from 'user/user-model';
import { officialMainLayoutContext } from 'common/common-model';

type LocationTrackElementListingSearchProps = {
    state: ElementListContinuousGeometrySearchState;
    setElements: (elements: ElementItem[]) => void;
    onUpdateProp: <TKey extends keyof ContinuousSearchParameters>(
        propEdit: PropEdit<ContinuousSearchParameters, TKey>,
    ) => void;
    onCommitField: <TKey extends keyof ContinuousSearchParameters>(key: TKey) => void;
    setLoading: (isLoading: boolean) => void;
};

const debouncedTrackElementsFetch = debounceAsync(getLocationTrackElements, 250);

const LocationTrackElementListingSearch = ({
    state,
    onUpdateProp,
    onCommitField,
    setElements,
    setLoading,
}: LocationTrackElementListingSearchProps) => {
    const { t } = useTranslation();

    // Use memoized function to make debouncing functionality work when re-rendering
    const getLocationTracks = React.useCallback(
        (searchTerm: string) =>
            debouncedSearchTracks(searchTerm, officialMainLayoutContext(), 10).then(
                (locationTracks) =>
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

    const [elementList, fetchStatus] = useLoaderWithStatus(() => {
        return !state.searchParameters.locationTrack ||
            hasErrors(state.committedFields, state.validationIssues, 'searchGeometries') ||
            hasErrors(state.committedFields, state.validationIssues, 'startTrackMeter') ||
            hasErrors(state.committedFields, state.validationIssues, 'endTrackMeter')
            ? Promise.resolve(state.elements)
            : debouncedTrackElementsFetch(
                  state.searchParameters.locationTrack.id,
                  selectedElementTypes(state.searchParameters.searchGeometries),
                  validTrackMeterOrUndefined(state.searchParameters.startTrackMeter),
                  validTrackMeterOrUndefined(state.searchParameters.endTrackMeter),
              );
    }, [state.searchParameters]);

    React.useEffect(() => setElements(elementList ?? []), [elementList]);
    React.useEffect(() => setLoading(fetchStatus !== LoaderStatus.Ready), [fetchStatus]);

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
                            qaId="data-products-search-location-track"
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
                            qa-id="data-products-search-start-km"
                            value={state.searchFields.startTrackMeter}
                            onChange={(e) => updateProp('startTrackMeter', e.target.value)}
                            onBlur={() => onCommitField('startTrackMeter')}
                            hasError={hasErrors(
                                state.committedFields,
                                state.validationIssues,
                                'startTrackMeter',
                            )}
                            wide
                        />
                    }
                    errors={getVisibleErrorsByProp(
                        state.committedFields,
                        state.validationIssues,
                        'startTrackMeter',
                    ).map((error) => t(`data-products.search.${error}`))}
                />
                <FieldLayout
                    label={t('data-products.search.track-address-end')}
                    value={
                        <TextField
                            qa-id="data-products-search-end-km"
                            value={state.searchFields.endTrackMeter}
                            onChange={(e) => updateProp('endTrackMeter', e.target.value)}
                            onBlur={() => onCommitField('endTrackMeter')}
                            hasError={hasErrors(
                                state.committedFields,
                                state.validationIssues,
                                'endTrackMeter',
                            )}
                            wide
                        />
                    }
                    errors={getVisibleErrorsByProp(
                        state.committedFields,
                        state.validationIssues,
                        'endTrackMeter',
                    ).map((error) => t(`data-products.search.${error}`))}
                />
                <div className={styles['data-products__search--no-label']}>
                    <FieldLayout
                        label={''}
                        value={
                            <div className={styles['element-list__geometry-checkbox']}>
                                <Checkbox
                                    qaId="data-products.search.line"
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
                                    qaId="data-products.search.curve"
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
                                    qaId="data-products.search.clothoid"
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
                                    qaId="data-products.search.missing-section"
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
                            state.validationIssues,
                            'searchGeometries',
                        ).map((error) => t(`data-products.search.${error}`))}
                    />
                </div>
                <PrivilegeRequired privilege={DOWNLOAD_GEOMETRY}>
                    <a
                        qa-id={'location-track-element-list-csv-download'}
                        {...(state.searchParameters.locationTrack && {
                            href: getLocationTrackElementsCsv(
                                state.searchParameters.locationTrack?.id,
                                selectedElementTypes(state.searchParameters.searchGeometries),
                                validTrackMeterOrUndefined(state.searchParameters.startTrackMeter),
                                validTrackMeterOrUndefined(state.searchParameters.endTrackMeter),
                            ),
                        })}>
                        <Button
                            className={styles['element-list__download-button']}
                            disabled={
                                !state.elements ||
                                state.elements.length === 0 ||
                                state.searchParameters.locationTrack === undefined
                            }
                            icon={Icons.Download}>
                            {t(`data-products.search.download-csv`)}
                        </Button>
                    </a>
                </PrivilegeRequired>
            </div>
        </React.Fragment>
    );
};

export default LocationTrackElementListingSearch;
