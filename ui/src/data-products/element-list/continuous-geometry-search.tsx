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
import { isNullOrBlank } from 'utils/string-utils';
import { getLocationTracksBySearchTerm } from 'track-layout/layout-location-track-api';
import { debounceAsync } from 'utils/async-utils';
import { PropEdit } from 'utils/validation-utils';
import { useLoader } from 'utils/react-utils';
import { GEOMETRY_URI, getLocationTrackElements } from 'geometry/geometry-api';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { queryParams } from 'api/api-fetch';
import { Button } from 'vayla-design-lib/button/button';

type ContinuousGeometrySearchProps = {
    state: ElementListContinuousGeometrySearchState;
    onUpdateProp: <TKey extends keyof ContinuousSearchParameters>(
        propEdit: PropEdit<ContinuousSearchParameters, TKey>,
    ) => void;
    onCommitField: <TKey extends keyof ContinuousSearchParameters>(key: TKey) => void;
};

const ContinuousGeometrySearch = ({
    state,
    onUpdateProp,
    onCommitField,
}: ContinuousGeometrySearchProps) => {
    const { t } = useTranslation();
    const [selectedLocationTrack, setSelectedLocationTrack] = React.useState<LayoutLocationTrack>();

    function searchLocationTracks(searchTerm: string): Promise<LayoutLocationTrack[]> {
        if (isNullOrBlank(searchTerm)) {
            return Promise.resolve([]);
        }

        return getLocationTracksBySearchTerm(searchTerm, 'OFFICIAL', 10);
    }
    // Use debounced function to collect keystrokes before triggering a search
    const debouncedGetLocationTrackOptions = debounceAsync(searchLocationTracks, 250);
    // Use memoized function to make debouncing functionality to work when re-rendering
    const getDuplicateTrackOptions = React.useCallback(
        (searchTerm) =>
            debouncedGetLocationTrackOptions(searchTerm).then((locationTracks) =>
                locationTracks
                    .filter((lt) => {
                        return (
                            !selectedLocationTrack ||
                            (selectedLocationTrack && lt.id !== selectedLocationTrack.id)
                        );
                    })
                    .map((lt) => ({
                        name: `${lt.name}, ${lt.description}`,
                        value: lt,
                    })),
            ),
        [selectedLocationTrack],
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

    const searchQueryParameters = queryParams({
        elementTypes: selectedElementTypes(state.searchParameters.searchGeometries),
        startAddress: validTrackMeterOrUndefined(state.searchParameters.startTrackMeter),
        endAddress: validTrackMeterOrUndefined(state.searchParameters.endTrackMeter),
    });

    // TODO Use plans when table is added
    const _plans = useLoader(() => {
        if (!selectedLocationTrack || hasErrors('searchGeometries')) return Promise.resolve([]);

        return getLocationTrackElements(
            selectedLocationTrack.id,
            selectedElementTypes(state.searchParameters.searchGeometries),
            validTrackMeterOrUndefined(state.searchParameters.startTrackMeter),
            validTrackMeterOrUndefined(state.searchParameters.endTrackMeter),
        );
    }, [selectedLocationTrack, state.searchParameters]);

    const downloadUri = `${GEOMETRY_URI}/layout/location-tracks/${selectedLocationTrack?.id}/element-listing/file${searchQueryParameters}`;

    return (
        <div className={styles['element-list__geometry-search']}>
            <FieldLayout
                label={t('data-products.element-list.search.location-track')}
                value={
                    <Dropdown
                        value={selectedLocationTrack}
                        getName={(item) => item.name}
                        placeholder={t('location-track-dialog.search')}
                        options={getDuplicateTrackOptions}
                        searchable
                        onChange={setSelectedLocationTrack}
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
                                checked={state.searchFields.searchGeometries.searchMissingGeometry}
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
                disabled={!_plans || _plans.length === 0}
                onClick={() => (location.href = downloadUri)}>
                <Icons.Download /> {t(`data-products.element-list.search.download-csv`)}
            </Button>
        </div>
    );
};

export default ContinuousGeometrySearch;
