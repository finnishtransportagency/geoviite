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
import {
    GEOMETRY_URI,
    getGeometryPlanElements,
    getGeometryPlanHeaders,
} from 'geometry/geometry-api';
import { GeometryPlanHeader } from 'geometry/geometry-model';
import { useLoader } from 'utils/react-utils';
import { queryParams } from 'api/api-fetch';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { ElementTable } from 'data-products/element-list/element-table';

type ContinuousGeometrySearchProps = {
    state: PlanGeometrySearchState;
    onUpdateProp: <TKey extends keyof PlanGeometrySearchState>(
        propEdit: PropEdit<PlanGeometrySearchState, TKey>,
    ) => void;
};

const PlanGeometrySearch = ({ state, onUpdateProp }: ContinuousGeometrySearchProps) => {
    const { t } = useTranslation();
    const [selectedPlanHeader, setSelectedPlanHeader] = React.useState<GeometryPlanHeader>();

    function searchGeometryPlanHeaders(searchTerm: string): Promise<GeometryPlanHeader[]> {
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
    const debouncedGetGeometryPlanHeaders = debounceAsync(searchGeometryPlanHeaders, 250);
    // Use memoized function to make debouncing functionality to work when re-rendering
    const geometryPlanHeaders = React.useCallback(
        (searchTerm) =>
            debouncedGetGeometryPlanHeaders(searchTerm).then((planHeaders) =>
                planHeaders
                    .filter((plan) => {
                        return (
                            !selectedPlanHeader ||
                            (selectedPlanHeader && plan.id !== selectedPlanHeader.id)
                        );
                    })
                    .map((plan) => ({
                        name: plan.fileName,
                        value: plan,
                    })),
            ),
        [selectedPlanHeader],
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
                  .map((error) => t(`data-products.element-list.search.${error.reason}`))
            : [];
    }

    function hasErrors(prop: keyof PlanGeometrySearchState) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    const searchQueryParameters = queryParams({
        elementTypes: selectedElementTypes(state.searchGeometries),
    });

    const canSearch = selectedPlanHeader && !hasErrors('searchGeometries');

    const elementList = useLoader(() => {
        if (!canSearch) return Promise.resolve([]);

        return getGeometryPlanElements(
            selectedPlanHeader.id,
            selectedElementTypes(state.searchGeometries),
        );
    }, [selectedPlanHeader, state.searchGeometries]);

    const downloadUri = `${GEOMETRY_URI}/plans/${selectedPlanHeader?.id}/element-listing/file${searchQueryParameters}`;

    return (
        <React.Fragment>
            <div className={styles['element-list__geometry-search']}>
                <div className={styles['element-list__plan-search-dropdown']}>
                    <FieldLayout
                        label={t(`data-products.element-list.search.plan`)}
                        value={
                            <Dropdown
                                value={selectedPlanHeader}
                                getName={(item: GeometryPlanHeader) => item.fileName}
                                placeholder={t('location-track-dialog.search')}
                                options={geometryPlanHeaders}
                                searchable
                                onChange={setSelectedPlanHeader}
                                canUnselect={true}
                                unselectText={t('data-products.element-list.search.not-selected')}
                                wideList
                                wide
                            />
                        }
                    />
                </div>
                <div className={styles['element-list__geometry-checkboxes']}>
                    <FieldLayout
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
                                    {t(`data-products.element-list.search.line`)}
                                </Checkbox>
                                <Checkbox
                                    checked={state.searchGeometries.searchCurves}
                                    onChange={(e) =>
                                        updateProp('searchGeometries', {
                                            ...state.searchGeometries,
                                            searchCurves: e.target.checked,
                                        })
                                    }>
                                    {t(`data-products.element-list.search.curve`)}
                                </Checkbox>
                                <Checkbox
                                    checked={state.searchGeometries.searchClothoids}
                                    onChange={(e) =>
                                        updateProp('searchGeometries', {
                                            ...state.searchGeometries,
                                            searchClothoids: e.target.checked,
                                        })
                                    }>
                                    {t(`data-products.element-list.search.clothoid`)}
                                </Checkbox>
                            </div>
                        }
                        errors={getVisibleErrorsByProp('searchGeometries')}
                    />
                </div>
                <Button
                    className={styles['element-list__download-button']}
                    disabled={!elementList || elementList.length === 0}
                    onClick={() => (location.href = downloadUri)}>
                    <Icons.Download /> {t(`data-products.element-list.search.download-csv`)}
                </Button>
            </div>
            {elementList && <ElementTable plans={elementList} />}
        </React.Fragment>
    );
};

export default PlanGeometrySearch;
