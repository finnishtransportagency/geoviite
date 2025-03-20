import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { debounceAsync } from 'utils/async-utils';
import { getVisibleErrorsByProp, PropEdit } from 'utils/validation-utils';
import { getGeometryPlanElements, getGeometryPlanElementsCsv } from 'geometry/geometry-api';
import { ElementItem, GeometryPlanHeader, PlanSource } from 'geometry/geometry-model';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { planSources } from 'utils/enum-localization-utils';
import { Radio } from 'vayla-design-lib/radio/radio';
import {
    debouncedGetGeometryPlanHeaders,
    getGeometryPlanOptions,
    getPlanFullName,
} from 'data-products/data-products-utils';
import { PlanGeometrySearchState, selectedElementTypes } from 'data-products/data-products-slice';
import { PrivilegeRequired } from 'user/privilege-required';
import { DOWNLOAD_GEOMETRY } from 'user/user-model';

type PlanGeometryElementListingSearchProps = {
    state: PlanGeometrySearchState;
    onUpdateProp: <TKey extends keyof PlanGeometrySearchState>(
        propEdit: PropEdit<PlanGeometrySearchState, TKey>,
    ) => void;
    setElements: (elements: ElementItem[]) => void;
    setLoading: (isLoading: boolean) => void;
};

const debouncedGetPlanElements = debounceAsync(getGeometryPlanElements, 250);

const PlanGeometryElementListingSearch = ({
    state,
    onUpdateProp,
    setElements,
    setLoading,
}: PlanGeometryElementListingSearchProps) => {
    const { t } = useTranslation();
    // Use memoized function to make debouncing functionality work when re-rendering
    const geometryPlanHeaders = React.useCallback(
        (searchTerm: string) =>
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
            getVisibleErrorsByProp(state.committedFields, state.validationIssues, prop).length > 0
        );
    }

    const [elementList, fetchStatus] = useLoaderWithStatus(() => {
        return !state.plan || hasErrors('searchGeometries')
            ? Promise.resolve([])
            : debouncedGetPlanElements(state.plan.id, selectedElementTypes(state.searchGeometries));
    }, [state.plan, state.searchGeometries]);

    const setSource = (source: PlanSource) => {
        updateProp('source', source);
        updateProp('plan', undefined);
    };

    React.useEffect(() => setElements(elementList ?? []), [elementList]);
    React.useEffect(() => setLoading(fetchStatus !== LoaderStatus.Ready), [fetchStatus]);

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
                                getName={(item: GeometryPlanHeader) => getPlanFullName(item)}
                                placeholder={t('data-products.search.search')}
                                options={geometryPlanHeaders}
                                searchable
                                onChange={(e) => updateProp('plan', e)}
                                unselectText={t('data-products.search.not-selected')}
                                wideList
                                wide
                                qaId={'data-products-search-plan'}
                            />
                        }
                    />
                </div>
                <div className={styles['data-products__search--no-label']}>
                    <FieldLayout
                        value={
                            <div className={styles['element-list__geometry-checkbox']}>
                                <Checkbox
                                    qaId="data-products.search.line"
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
                                    qaId="data-products.search.curve"
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
                                    qaId="data-products.search.clothoid"
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
                            state.validationIssues,
                            'searchGeometries',
                        ).map((error) => t(`data-products.search.${error}`))}
                    />
                </div>
                <PrivilegeRequired privilege={DOWNLOAD_GEOMETRY}>
                    <a
                        qa-id={'plan-element-list-csv-download'}
                        {...(state.plan && {
                            href: getGeometryPlanElementsCsv(
                                state.plan?.id,
                                selectedElementTypes(state.searchGeometries),
                            ),
                        })}>
                        <Button
                            className={styles['element-list__download-button']}
                            disabled={!state.elements || state.elements.length === 0}
                            icon={Icons.Download}>
                            {t(`data-products.search.download-csv`)}
                        </Button>
                    </a>
                </PrivilegeRequired>
            </div>
        </React.Fragment>
    );
};

export default PlanGeometryElementListingSearch;
