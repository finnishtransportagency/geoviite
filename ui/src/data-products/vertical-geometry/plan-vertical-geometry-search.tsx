import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { planSources } from 'utils/enum-localization-utils';
import { Radio } from 'vayla-design-lib/radio/radio';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { GeometryPlanHeader, PlanSource } from 'geometry/geometry-model';
import {
    getGeometryPlanHeadersBySearchTerms,
    getGeometryPlanVerticalGeometry,
} from 'geometry/geometry-api';
import { PlanVerticalGeometrySearchState } from 'data-products/element-list/element-list-store';
import { PropEdit } from 'utils/validation-utils';
import { isNullOrBlank } from 'utils/string-utils';
import { debounceAsync } from 'utils/async-utils';
import { useLoader } from 'utils/react-utils';

type PlanVerticalGeometrySearchProps = {
    state: PlanVerticalGeometrySearchState;
    onUpdateProp: <TKey extends keyof PlanVerticalGeometrySearchState>(
        propEdit: PropEdit<PlanVerticalGeometrySearchState, TKey>,
    ) => void;
    setVerticalGeometry: (verticalGeometry: never[]) => void;
};

function searchGeometryPlanHeaders(
    source: PlanSource,
    searchTerm: string,
): Promise<GeometryPlanHeader[]> {
    if (isNullOrBlank(searchTerm)) {
        return Promise.resolve([]);
    }

    return getGeometryPlanHeadersBySearchTerms(
        10,
        undefined,
        undefined,
        [source],
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
const debouncedGetPlanElements = debounceAsync(getGeometryPlanVerticalGeometry, 250);

export const PlanVerticalGeometrySearch: React.FC<PlanVerticalGeometrySearchProps> = ({
    state,
    onUpdateProp,
    setVerticalGeometry,
}) => {
    const { t } = useTranslation();
    // Use memoized function to make debouncing functionality work when re-rendering
    const geometryPlanHeaders = React.useCallback(
        (searchTerm) =>
            debouncedGetGeometryPlanHeaders(state.source, searchTerm).then((planHeaders) =>
                getGeometryPlanOptions(planHeaders, state.plan),
            ),
        [state.source, state.plan],
    );

    function updateProp<TKey extends keyof PlanVerticalGeometrySearchState>(
        key: TKey,
        value: PlanVerticalGeometrySearchState[TKey],
    ) {
        onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: false,
        });
    }

    const setSource = (source: PlanSource) => {
        updateProp('source', source);
        updateProp('plan', undefined);
    };

    const verticalGeometries = useLoader(() => {
        return !state.plan ? Promise.resolve([]) : debouncedGetPlanElements(state.plan.id);
    }, [state.plan]);
    React.useEffect(() => setVerticalGeometry(verticalGeometries ?? []), [verticalGeometries]);

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.vertical-geometry.plan-search-legend')}
            </p>
            <div className={styles['data-products__search']}>
                <FieldLayout
                    label={t(`data-products.element-list.search.source`)}
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
                {/*<Button
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
                </Button>*/}
            </div>
        </React.Fragment>
    );
};

export default PlanVerticalGeometrySearch;
