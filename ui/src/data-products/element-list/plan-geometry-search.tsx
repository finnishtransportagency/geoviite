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
    getGeometryPlanElements,
    getGeometryPlanElementsCsv,
    getGeometryPlanHeaders,
} from 'geometry/geometry-api';
import { ElementItem, GeometryPlanHeader } from 'geometry/geometry-model';
import { useLoader } from 'utils/react-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';

type ContinuousGeometrySearchProps = {
    state: PlanGeometrySearchState;
    onUpdateProp: <TKey extends keyof PlanGeometrySearchState>(
        propEdit: PropEdit<PlanGeometrySearchState, TKey>,
    ) => void;
    setElements: (elements: ElementItem[]) => void;
};

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

function getGeometryPlanOptions(
    headers: GeometryPlanHeader[],
    selectedHeader: GeometryPlanHeader | undefined,
) {
    return headers
        .filter((plan) => !selectedHeader || plan.id !== selectedHeader.id)
        .map((plan) => ({ name: plan.fileName, value: plan }));
}

const debouncedGetGeometryPlanHeaders = debounceAsync(searchGeometryPlanHeaders, 250);
const debouncedGetPlanElements = debounceAsync(getGeometryPlanElements, 250);

const PlanGeometrySearch = ({
    state,
    onUpdateProp,
    setElements,
}: ContinuousGeometrySearchProps) => {
    const { t } = useTranslation();

    // Use memoized function to make debouncing functionality work when re-rendering
    const geometryPlanHeaders = React.useCallback(
        (searchTerm) =>
            debouncedGetGeometryPlanHeaders(searchTerm).then((planHeaders) =>
                getGeometryPlanOptions(planHeaders, state.plan),
            ),
        [state.plan],
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

    const elementList = useLoader(() => {
        return !state.plan || hasErrors('searchGeometries')
            ? Promise.resolve([])
            : debouncedGetPlanElements(state.plan.id, selectedElementTypes(state.searchGeometries));
    }, [state.plan, state.searchGeometries]);

    React.useEffect(() => setElements(elementList ?? []), [elementList]);

    return (
        <React.Fragment>
            <div className={styles['element-list__geometry-search']}>
                <div className={styles['element-list__plan-search-dropdown']}>
                    <FieldLayout
                        label={t(`data-products.element-list.search.plan`)}
                        value={
                            <Dropdown
                                value={state.plan}
                                getName={(item: GeometryPlanHeader) => item.fileName}
                                placeholder={t('location-track-dialog.search')}
                                options={geometryPlanHeaders}
                                searchable
                                onChange={(e) => updateProp('plan', e)}
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
                    disabled={!state.elements || state.elements.length === 0}
                    onClick={() => {
                        if (state.plan) {
                            location.href = getGeometryPlanElementsCsv(
                                state.plan?.id,
                                selectedElementTypes(state.searchGeometries),
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

export default PlanGeometrySearch;
