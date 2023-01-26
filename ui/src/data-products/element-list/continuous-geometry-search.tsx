import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/element-list/element-list-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { ElementListContinuousGeometrySearchState } from 'data-products/element-list/element-list-store';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';
import { isNullOrBlank } from 'utils/string-utils';
import { getLocationTracksBySearchTerm } from 'track-layout/layout-location-track-api';
import { debounceAsync } from 'utils/async-utils';
import { PropEdit } from 'utils/validation-utils';

type ContinuousGeometrySearchProps = {
    state: ElementListContinuousGeometrySearchState;
    onUpdateProp: <TKey extends keyof ElementListContinuousGeometrySearchState>(
        propEdit: PropEdit<ElementListContinuousGeometrySearchState, TKey>,
    ) => void;
    onCommitField: <TKey extends keyof ElementListContinuousGeometrySearchState>(key: TKey) => void;
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

    function updateProp<TKey extends keyof ElementListContinuousGeometrySearchState>(
        key: TKey,
        value: ElementListContinuousGeometrySearchState[TKey],
    ) {
        onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: false,
        });
    }

    function getVisibleErrorsByProp(prop: keyof ElementListContinuousGeometrySearchState) {
        return state.committedFields.includes(prop)
            ? state.validationErrors
                  .filter((error) => error.field == prop)
                  .map((error) => t(`element-list.continuous-search.${error.reason}`))
            : [];
    }

    function hasErrors(prop: keyof ElementListContinuousGeometrySearchState) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    return (
        <div className={styles['element-list__geometry-search']}>
            <FieldLayout
                label={`Sijaintiraide`}
                value={
                    <Dropdown
                        value={selectedLocationTrack}
                        getName={(item) => item.name}
                        placeholder={t('location-track-dialog.search')}
                        options={getDuplicateTrackOptions}
                        searchable
                        onChange={setSelectedLocationTrack}
                        canUnselect={true}
                        unselectText={t('location-track-dialog.not-a-duplicate')}
                        wideList
                        wide
                    />
                }
            />
            <FieldLayout
                label={`Rataosoitteen alku`}
                value={
                    <TextField
                        value={state.startTrackMeter}
                        onChange={(e) => updateProp('startTrackMeter', e.target.value)}
                        onBlur={() => onCommitField('startTrackMeter')}
                        hasError={hasErrors('startTrackMeter')}
                        wide
                    />
                }
                errors={getVisibleErrorsByProp('startTrackMeter')}
            />
            <FieldLayout
                label={`Ratosoitteen loppu`}
                value={
                    <TextField
                        value={state.endTrackMeter}
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
                                checked={state.searchGeometries.searchLines}
                                onChange={(e) =>
                                    updateProp('searchGeometries', {
                                        ...state.searchGeometries,
                                        searchLines: e.target.checked,
                                    })
                                }>
                                Suora
                            </Checkbox>
                            <Checkbox
                                checked={state.searchGeometries.searchCurves}
                                onChange={(e) =>
                                    updateProp('searchGeometries', {
                                        ...state.searchGeometries,
                                        searchCurves: e.target.checked,
                                    })
                                }>
                                Kaari
                            </Checkbox>
                            <Checkbox
                                checked={state.searchGeometries.searchClothoids}
                                onChange={(e) =>
                                    updateProp('searchGeometries', {
                                        ...state.searchGeometries,
                                        searchClothoids: e.target.checked,
                                    })
                                }>
                                Siirtymäkaari
                            </Checkbox>
                            <Checkbox
                                checked={state.searchGeometries.searchMissingGeometry}
                                onChange={(e) =>
                                    updateProp('searchGeometries', {
                                        ...state.searchGeometries,
                                        searchMissingGeometry: e.target.checked,
                                    })
                                }>
                                Puuttuva osuus
                            </Checkbox>
                        </div>
                    }
                    errors={getVisibleErrorsByProp('searchGeometries')}
                />
            </div>
        </div>
    );
};

export default ContinuousGeometrySearch;
