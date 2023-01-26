import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/element-list/element-list-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import {
    PlanGeometrySearchState,
    selectedElementTypes,
} from 'data-products/element-list/element-list-store';
import { isNullOrBlank } from 'utils/string-utils';
import { debounceAsync } from 'utils/async-utils';
import { PropEdit } from 'utils/validation-utils';
import { getGeometryPlanElements, getGeometryPlanHeaders } from 'geometry/geometry-api';
import { GeometryPlanHeader } from 'geometry/geometry-model';
import { useLoader } from 'utils/react-utils';

type ContinuousGeometrySearchProps = {
    state: PlanGeometrySearchState;
    onUpdateProp: <TKey extends keyof PlanGeometrySearchState>(
        propEdit: PropEdit<PlanGeometrySearchState, TKey>,
    ) => void;
};

const PlanGeometrySearch = ({ state, onUpdateProp }: ContinuousGeometrySearchProps) => {
    const { t } = useTranslation();
    const [selectedLocationTrack, setSelectedLocationTrack] = React.useState<GeometryPlanHeader>();

    function searchLocationTracks(searchTerm: string): Promise<GeometryPlanHeader[]> {
        if (isNullOrBlank(searchTerm)) {
            return Promise.resolve([]);
        }

        return getGeometryPlanHeaders(
            10,
            undefined,
            undefined,
            ['GEOVIITE', 'GEOMETRIAPALVELU', 'PAIKANNUSPALVELU'],
            [],
            searchTerm,
        ).then((t) => t.items);
    }
    // Use debounced function to collect keystrokes before triggering a search
    const debouncedGetLocationTrackOptions = debounceAsync(searchLocationTracks, 250);
    // Use memoized function to make debouncing functionality to work when re-rendering
    const getDuplicateTrackOptions = React.useCallback(
        (searchTerm) =>
            debouncedGetLocationTrackOptions(searchTerm).then((planHeaders) =>
                planHeaders
                    .filter((lt) => {
                        return (
                            !selectedLocationTrack ||
                            (selectedLocationTrack && lt.id !== selectedLocationTrack.id)
                        );
                    })
                    .map((lt) => ({
                        name: lt.fileName,
                        value: lt,
                    })),
            ),
        [selectedLocationTrack],
    );

    function updateProp<TKey extends keyof PlanGeometrySearchState>(
        key: TKey,
        value: PlanGeometrySearchState[TKey],
    ) {
        onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: false,
        });
    }

    function getVisibleErrorsByProp(prop: keyof PlanGeometrySearchState) {
        return state.committedFields.includes(prop)
            ? state.validationErrors
                  .filter((error) => error.field == prop)
                  .map((error) => t(`element-list.continuous-search.${error.reason}`))
            : [];
    }

    function hasErrors(prop: keyof PlanGeometrySearchState) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    const moi = selectedLocationTrack && !hasErrors('searchGeometries');
    const _plans = useLoader(() => {
        if (!moi) return Promise.resolve([]);

        return getGeometryPlanElements(
            selectedLocationTrack.id,
            selectedElementTypes(state.searchGeometries),
        );
    }, [selectedLocationTrack, state.searchGeometries]);

    return (
        <div className={styles['element-list__geometry-search']}>
            <div className={styles['element-list__plan-search-dropdown']}>
                <FieldLayout
                    label={`Suunnitelma`}
                    value={
                        <Dropdown
                            value={selectedLocationTrack}
                            getName={(item: GeometryPlanHeader) => item.fileName}
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
            </div>
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
                        </div>
                    }
                    errors={getVisibleErrorsByProp('searchGeometries')}
                />
            </div>
        </div>
    );
};

export default PlanGeometrySearch;
