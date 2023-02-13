import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/element-list/element-list-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import {
    ContinuousSearchParameters,
    ElementListContinuousGeometrySearchState,
    selectedElementTypes,
    validTrackMeterOrUndefined,
} from 'data-products/element-list/element-list-store';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';
import { getLocationTracksBySearchTerm } from 'track-layout/layout-location-track-api';
import { debounceAsync } from 'utils/async-utils';
import { PropEdit } from 'utils/validation-utils';
import { useLoader } from 'utils/react-utils';
import { getLocationTrackElements, getLocationTrackElementsCsv } from 'geometry/geometry-api';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { ElementItem } from 'geometry/geometry-model';

type ContinuousGeometrySearchProps = {
    state: ElementListContinuousGeometrySearchState;
    setElements: (elements: ElementItem[]) => void;
    onUpdateProp: <TKey extends keyof ContinuousSearchParameters>(
        propEdit: PropEdit<ContinuousSearchParameters, TKey>,
    ) => void;
    onCommitField: <TKey extends keyof ContinuousSearchParameters>(key: TKey) => void;
};

function getLocationTrackOptions(
    tracks: LayoutLocationTrack[],
    selectedTrack: LayoutLocationTrack | undefined,
) {
    return tracks
        .filter((lt) => !selectedTrack || lt.id !== selectedTrack.id)
        .map((lt) => ({ name: `${lt.name}, ${lt.description}`, value: lt }));
}

const debouncedSearchTracks = debounceAsync(getLocationTracksBySearchTerm, 250);
const debouncedTrackElementsFetch = debounceAsync(getLocationTrackElements, 250);

const ContinuousGeometrySearch = ({
    state,
    onUpdateProp,
    onCommitField,
    setElements,
}: ContinuousGeometrySearchProps) => {
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

    function getVisibleErrorsByProp(prop: keyof ContinuousSearchParameters) {
        return state.committedFields.includes(prop)
            ? state.validationErrors
                .filter((error) => error.field == prop)
                .map((error) => t(`data-products.element-list.search.${error.reason}`))
            : [];
    }

    function hasErrors(prop: keyof ContinuousSearchParameters) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    const elementList = useLoader(() => {
        return !state.searchParameters.locationTrack || hasErrors('searchGeometries')
            ? Promise.resolve([])
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
            <div className={styles['element-list__geometry-search']}>
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
                <div className={styles['element-list__geometry-checkboxes']}>
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
                                    {t('data-products.element-list.search.line')}
                                </Checkbox>
                                <Checkbox
                                    checked={state.searchFields.searchGeometries.searchCurves}
                                    onChange={(e) =>
                                        updateProp('searchGeometries', {
                                            ...state.searchFields.searchGeometries,
                                            searchCurves: e.target.checked,
                                        })
                                    }>
                                    {t('data-products.element-list.search.curve')}
                                </Checkbox>
                                <Checkbox
                                    checked={state.searchFields.searchGeometries.searchClothoids}
                                    onChange={(e) =>
                                        updateProp('searchGeometries', {
                                            ...state.searchFields.searchGeometries,
                                            searchClothoids: e.target.checked,
                                        })
                                    }>
                                    {t('data-products.element-list.search.clothoid')}
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
                                    {t('data-products.element-list.search.missing-section')}
                                </Checkbox>
                            </div>
                        }
                        errors={getVisibleErrorsByProp('searchGeometries')}
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
                    {t(`data-products.element-list.search.download-csv`)}
                </Button>
            </div>
        </React.Fragment>
    );
};

export default ContinuousGeometrySearch;
