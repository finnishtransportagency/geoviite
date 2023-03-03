import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { debounceAsync } from 'utils/async-utils';
import { getLocationTracksBySearchTerm } from 'track-layout/layout-location-track-api';
import {
    LocationTrackVerticalGeometrySearchParameters,
    LocationTrackVerticalGeometrySearchState,
    validTrackMeterOrUndefined,
} from 'data-products/element-list/element-list-store';
import { PropEdit } from 'utils/validation-utils';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';
import { getLocationTrackVerticalGeometry } from 'geometry/geometry-api';
import { useLoader } from 'utils/react-utils';

type LocationTrackVerticalGeometrySearchProps = {
    state: LocationTrackVerticalGeometrySearchState;
    onUpdateProp: <TKey extends keyof LocationTrackVerticalGeometrySearchParameters>(
        propEdit: PropEdit<LocationTrackVerticalGeometrySearchParameters, TKey>,
    ) => void;
    onCommitField: <TKey extends keyof LocationTrackVerticalGeometrySearchParameters>(
        key: TKey,
    ) => void;
    setVerticalGeometry: (verticalGeometry: never[]) => void;
};

const debouncedSearchTracks = debounceAsync(getLocationTracksBySearchTerm, 250);
const debouncedTrackElementsFetch = debounceAsync(getLocationTrackVerticalGeometry, 250);

function getLocationTrackOptions(
    tracks: LayoutLocationTrack[],
    selectedTrack: LayoutLocationTrack | undefined,
) {
    return tracks
        .filter((lt) => !selectedTrack || lt.id !== selectedTrack.id)
        .map((lt) => ({ name: `${lt.name}, ${lt.description}`, value: lt }));
}

export const LocationTrackVerticalGeometrySearch: React.FC<
    LocationTrackVerticalGeometrySearchProps
> = ({ state, onCommitField, onUpdateProp, setVerticalGeometry }) => {
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

    function getVisibleErrorsByProp(prop: keyof LocationTrackVerticalGeometrySearchParameters) {
        return state.committedFields.includes(prop)
            ? state.validationErrors
                  .filter((error) => error.field == prop)
                  .map((error) => t(`data-products.element-list.search.${error.reason}`))
            : [];
    }

    function hasErrors(prop: keyof LocationTrackVerticalGeometrySearchParameters) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    const verticalGeometries = useLoader(() => {
        return !state.searchParameters.locationTrack ||
            hasErrors('startTrackMeter') ||
            hasErrors('endTrackMeter')
            ? Promise.resolve(state.verticalGeometry)
            : debouncedTrackElementsFetch(
                  state.searchParameters.locationTrack.id,
                  validTrackMeterOrUndefined(state.searchParameters.startTrackMeter),
                  validTrackMeterOrUndefined(state.searchParameters.endTrackMeter),
              );
    }, [state.searchParameters]);
    React.useEffect(() => setVerticalGeometry(verticalGeometries ?? []), [verticalGeometries]);

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.vertical-geometry.location-track-search-legend')}
            </p>
            <div className={styles['data-products__search']}>
                <FieldLayout
                    label={t('data-products.element-list.search.location-track')}
                    value={
                        <Dropdown
                            value={state.searchParameters.locationTrack}
                            getName={(item) => item.name}
                            placeholder={t('location-track-dialog.search')}
                            options={getLocationTracks}
                            searchable
                            onChange={(e) => updateProp('locationTrack', e)}
                            onBlur={() => onCommitField('locationTrack')}
                            canUnselect={true}
                            unselectText={t('data-products.element-list.search.not-selected')}
                            wideList
                            wide
                        />
                    }
                />
                <FieldLayout
                    label={t('data-products.element-list.search.track-address-start')}
                    value={
                        <TextField
                            value={state.searchFields.startTrackMeter}
                            onChange={(e) => updateProp('startTrackMeter', e.target.value)}
                            onBlur={() => onCommitField('startTrackMeter')}
                            hasError={hasErrors('startTrackMeter')}
                            wide
                        />
                    }
                    errors={getVisibleErrorsByProp('startTrackMeter')}
                />
                <FieldLayout
                    label={t('data-products.element-list.search.track-address-end')}
                    value={
                        <TextField
                            value={state.searchFields.endTrackMeter}
                            onChange={(e) => updateProp('endTrackMeter', e.target.value)}
                            onBlur={() => onCommitField('endTrackMeter')}
                            hasError={hasErrors('endTrackMeter')}
                            wide
                        />
                    }
                    errors={getVisibleErrorsByProp('endTrackMeter')}
                />
            </div>
        </React.Fragment>
    );
};

export default LocationTrackVerticalGeometrySearch;
