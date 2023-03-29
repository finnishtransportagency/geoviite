import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { PlanGeometrySearchState, selectedElementTypes } from 'data-products/data-products-store';
import { debounceAsync } from 'utils/async-utils';
import { PropEdit } from 'utils/validation-utils';
import { getGeometryPlanElements, getGeometryPlanElementsCsv } from 'geometry/geometry-api';
import { ElementItem, GeometryPlanHeader, PlanSource } from 'geometry/geometry-model';
import { useLoader } from 'utils/react-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { planSources } from 'utils/enum-localization-utils';
import { Radio } from 'vayla-design-lib/radio/radio';
import {
    debouncedGetGeometryPlanHeaders,
    getGeometryPlanOptions,
    getVisibleErrorsByProp,
} from 'data-products/data-products-utils';

type PlanGeometryElementListingSearchProps = {
    state: PlanGeometrySearchState;
    onUpdateProp: <TKey extends keyof PlanGeometrySearchState>(
        propEdit: PropEdit<PlanGeometrySearchState, TKey>,
    ) => void;
    setElements: (elements: ElementItem[]) => void;
};

const debouncedGetPlanElements = debounceAsync(getGeometryPlanElements, 250);

const PlanGeometryElementListingSearch = ({
    state,
    onUpdateProp,
    setElements,
}: PlanGeometryElementListingSearchProps) => {
    const { t } = useTranslation();
    // Use memoized function to make debouncing functionality work when re-rendering
    const geometryPlanHeaders = React.useCallback(
        (searchTerm) =>
            debouncedGetGeometryPlanHeaders(state.source, searchTerm).then((planHeaders) =>
                getGeometryPlanOptions(planHeaders, state.plan),
            ),
        [state.source, state.plan],
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

    function hasErrors(prop: keyof PlanGeometrySearchState) {
        return (
            getVisibleErrorsByProp(state.committedFields, state.validationErrors, prop).length > 0
        );
    }

    const elementList = useLoader(() => {
        return !state.plan || hasErrors('searchGeometries')
            ? Promise.resolve([])
            : debouncedGetPlanElements(state.plan.id, selectedElementTypes(state.searchGeometries));
    }, [state.plan, state.searchGeometries]);

    const setSource = (source: PlanSource) => {
        updateProp('source', source);
        updateProp('plan', undefined);
    };

    React.useEffect(() => setElements(elementList ?? []), [elementList]);

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.element-list.plan-legend')}
            </p>
            <div className={styles['data-products__search']}>
                <FieldLayout
                    label={t(`data-products.search.source`)}
                    value={
                        <>
                            {planSources.map((source) => (
                                <span
                                    key={source.value}
                                    className={styles['data-product-view__radio-layout']}>
                                    <Radio
                                        checked={state.source === source.value}
                                        onChange={() => setSource(source.value)}>
                                        {t(source.name)}
                                    </Radio>
                                </span>
                            ))}
                        </>
                    }
                />
                <div className={styles['element-list__plan-search-dropdown']}>
                    <FieldLayout
                        label={t(`data-products.search.plan`)}
                        value={
                            <Dropdown
                                value={state.plan}
                                getName={(item: GeometryPlanHeader) => item.fileName}
                                placeholder={t('location-track-dialog.search')}
                                options={geometryPlanHeaders}
                                searchable
                                onChange={(e) => updateProp('plan', e)}
                                unselectText={t('data-products.search.not-selected')}
                                wideList
                                wide
                            />
                        }
                    />
                </div>
                <div className={styles['data-products__search--no-label']}>
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
                                    {t(`data-products.search.line`)}
                                </Checkbox>
                                <Checkbox
                                    checked={state.searchGeometries.searchCurves}
                                    onChange={(e) =>
                                        updateProp('searchGeometries', {
                                            ...state.searchGeometries,
                                            searchCurves: e.target.checked,
                                        })
                                    }>
                                    {t(`data-products.search.curve`)}
                                </Checkbox>
                                <Checkbox
                                    checked={state.searchGeometries.searchClothoids}
                                    onChange={(e) =>
                                        updateProp('searchGeometries', {
                                            ...state.searchGeometries,
                                            searchClothoids: e.target.checked,
                                        })
                                    }>
                                    {t(`data-products.search.clothoid`)}
                                </Checkbox>
                            </div>
                        }
                        errors={getVisibleErrorsByProp(
                            state.committedFields,
                            state.validationErrors,
                            'searchGeometries',
                        ).map((error) => t(`data-products.search.${error}`))}
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
                    {t(`data-products.search.download-csv`)}
                </Button>
            </div>
        </React.Fragment>
    );
};

export default PlanGeometryElementListingSearch;
