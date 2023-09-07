import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { debounceAsync } from 'utils/async-utils';
import { PropEdit } from 'utils/validation-utils';
import {
    getLocationTrackVerticalGeometry,
    getLocationTrackVerticalGeometryCsv,
} from 'geometry/geometry-api';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { Button } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import {
    debouncedSearchTracks,
    getLocationTrackOptions,
    getVisibleErrorsByProp,
    hasErrors,
} from 'data-products/data-products-utils';
import { VerticalGeometryItem } from 'geometry/geometry-model';
import {
    LocationTrackVerticalGeometrySearchParameters,
    LocationTrackVerticalGeometrySearchState,
    validTrackMeterOrUndefined,
} from 'data-products/data-products-slice';

type LocationTrackVerticalGeometrySearchProps = {
    state: LocationTrackVerticalGeometrySearchState;
    onUpdateProp: <TKey extends keyof LocationTrackVerticalGeometrySearchParameters>(
        propEdit: PropEdit<LocationTrackVerticalGeometrySearchParameters, TKey>,
    ) => void;
    onCommitField: <TKey extends keyof LocationTrackVerticalGeometrySearchParameters>(
        key: TKey,
    ) => void;
    setVerticalGeometry: (verticalGeometry: VerticalGeometryItem[]) => void;
    setLoading: (loading: boolean) => void;
};

const debouncedTrackElementsFetch = debounceAsync(getLocationTrackVerticalGeometry, 250);

export const LocationTrackVerticalGeometrySearch: React.FC<
    LocationTrackVerticalGeometrySearchProps
> = ({ state, onCommitField, onUpdateProp, setVerticalGeometry, setLoading }) => {
    const { t } = useTranslation();
    const getLocationTracks = React.useCallback(
        (searchTerm) =>
            debouncedSearchTracks(searchTerm, 'OFFICIAL', 10).then((locationTracks) =>
                getLocationTrackOptions(locationTracks, state.searchParameters.locationTrack),
            ),
        [state.searchParameters.locationTrack],
    );

    function updateProp<TKey extends keyof LocationTrackVerticalGeometrySearchParameters>(
        key: TKey,
        value: LocationTrackVerticalGeometrySearchParameters[TKey],
    ) {
        onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: false,
        });
    }

    const [verticalGeometries, fetchStatus] = useLoaderWithStatus(() => {
        if (!state.searchParameters.locationTrack) {
            return Promise.resolve([]);
        }
        if (
            hasErrors(state.committedFields, state.validationErrors, 'startTrackMeter') ||
            hasErrors(state.committedFields, state.validationErrors, 'endTrackMeter')
        ) {
            return Promise.resolve(state.verticalGeometry);
        }

        return debouncedTrackElementsFetch(
            undefined,
            'OFFICIAL',
            state.searchParameters.locationTrack.id,
            validTrackMeterOrUndefined(state.searchParameters.startTrackMeter),
            validTrackMeterOrUndefined(state.searchParameters.endTrackMeter),
        );
    }, [state.searchParameters]);
    React.useEffect(() => setVerticalGeometry(verticalGeometries ?? []), [verticalGeometries]);
    React.useEffect(() => setLoading(fetchStatus !== LoaderStatus.Ready), [fetchStatus]);

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.vertical-geometry.location-track-search-legend')}
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
                <Button
                    className={styles['element-list__download-button']}
                    disabled={!state.verticalGeometry || state.verticalGeometry.length === 0}
                    onClick={() => {
                        if (state.searchParameters.locationTrack) {
                            location.href = getLocationTrackVerticalGeometryCsv(
                                state.searchParameters.locationTrack?.id,
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

export default LocationTrackVerticalGeometrySearch;
