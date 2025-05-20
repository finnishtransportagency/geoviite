import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { debounceAsync } from 'utils/async-utils';
import { getVisibleErrorsByProp, hasErrors, PropEdit } from 'utils/validation-utils';
import {
    getLocationTrackVerticalGeometry,
    getLocationTrackVerticalGeometryCsv,
} from 'geometry/geometry-api';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { Button } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { debouncedSearchTracks, getLocationTrackOptions } from 'data-products/data-products-utils';
import { VerticalGeometryItem } from 'geometry/geometry-model';
import {
    LocationTrackVerticalGeometrySearchParameters,
    LocationTrackVerticalGeometrySearchState,
    validTrackMeterOrUndefined,
} from 'data-products/data-products-slice';
import {
    getLocationTrackDescriptions,
    getLocationTrackNames,
} from 'track-layout/layout-location-track-api';
import { PrivilegeRequired } from 'user/privilege-required';
import { DOWNLOAD_GEOMETRY } from 'user/user-model';
import { officialMainLayoutContext } from 'common/common-model';
import { useLocationTrackName } from 'track-layout/track-layout-react-utils';

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
        (searchTerm: string) =>
            debouncedSearchTracks(searchTerm, officialMainLayoutContext(), 10)
                .then(async (locationTracks) => {
                    const ids = locationTracks.map((lt) => lt.id);
                    return {
                        locationTracks,
                        names: await getLocationTrackNames(ids, officialMainLayoutContext()),
                        descriptions: await getLocationTrackDescriptions(
                            ids,
                            officialMainLayoutContext(),
                        ),
                    };
                })
                .then(({ locationTracks, names, descriptions }) =>
                    getLocationTrackOptions(
                        locationTracks,
                        names ?? [],
                        descriptions ?? [],
                        state.searchParameters.locationTrack,
                    ),
                ),

        [state.searchParameters.locationTrack],
    );
    const searchParameterTrackName = useLocationTrackName(
        state.searchParameters.locationTrack?.id,
        officialMainLayoutContext(),
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
            hasErrors(state.committedFields, state.validationIssues, 'startTrackMeter') ||
            hasErrors(state.committedFields, state.validationIssues, 'endTrackMeter')
        ) {
            return Promise.resolve(state.verticalGeometry);
        }

        return debouncedTrackElementsFetch(
            undefined,
            officialMainLayoutContext(),
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
                            qaId={'data-products-search-location-track'}
                            value={{
                                locationTrack: state.searchParameters.locationTrack,
                                name: searchParameterTrackName,
                            }}
                            getName={(item) => item.name?.name ?? ''}
                            placeholder={t('data-products.search.search')}
                            options={getLocationTracks}
                            searchable
                            onChange={(e) => updateProp('locationTrack', e?.locationTrack)}
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
                <PrivilegeRequired privilege={DOWNLOAD_GEOMETRY}>
                    <a
                        qa-id="vertical-geometry-csv-download"
                        {...(state.searchParameters.locationTrack && {
                            href: getLocationTrackVerticalGeometryCsv(
                                state.searchParameters.locationTrack?.id,
                                validTrackMeterOrUndefined(state.searchParameters.startTrackMeter),
                                validTrackMeterOrUndefined(state.searchParameters.endTrackMeter),
                            ),
                        })}>
                        <Button
                            className={styles['element-list__download-button']}
                            disabled={
                                !state.verticalGeometry || state.verticalGeometry.length === 0
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

export default LocationTrackVerticalGeometrySearch;
