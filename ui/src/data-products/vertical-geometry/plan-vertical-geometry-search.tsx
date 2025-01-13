import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { planSources } from 'utils/enum-localization-utils';
import { Radio } from 'vayla-design-lib/radio/radio';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { GeometryPlanHeader, PlanSource, VerticalGeometryItem } from 'geometry/geometry-model';
import {
    getGeometryPlanVerticalGeometry,
    getGeometryPlanVerticalGeometryCsv,
} from 'geometry/geometry-api';
import { PropEdit } from 'utils/validation-utils';
import { debounceAsync } from 'utils/async-utils';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import {
    debouncedGetGeometryPlanHeaders,
    getGeometryPlanOptions,
    getPlanFullName,
} from 'data-products/data-products-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { PlanVerticalGeometrySearchState } from 'data-products/data-products-slice';
import { PrivilegeRequired } from 'user/privilege-required';
import { DOWNLOAD_GEOMETRY } from 'user/user-model';

type PlanVerticalGeometrySearchProps = {
    state: PlanVerticalGeometrySearchState;
    onUpdateProp: <TKey extends keyof PlanVerticalGeometrySearchState>(
        propEdit: PropEdit<PlanVerticalGeometrySearchState, TKey>,
    ) => void;
    setVerticalGeometry: (verticalGeometry: VerticalGeometryItem[]) => void;
    setLoading: (loading: boolean) => void;
};

const debouncedGetPlanVerticalGeometry = debounceAsync(getGeometryPlanVerticalGeometry, 250);

export const PlanVerticalGeometrySearch: React.FC<PlanVerticalGeometrySearchProps> = ({
    state,
    onUpdateProp,
    setVerticalGeometry,
    setLoading,
}) => {
    const { t } = useTranslation();
    // Use memoized function to make debouncing functionality work when re-rendering
    const geometryPlanHeaders = React.useCallback(
        (searchTerm: string) =>
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

    const [verticalGeometries, fetchStatus] = useLoaderWithStatus(() => {
        return !state.plan
            ? Promise.resolve([])
            : debouncedGetPlanVerticalGeometry(undefined, state.plan.id);
    }, [state.plan]);
    React.useEffect(() => setVerticalGeometry(verticalGeometries ?? []), [verticalGeometries]);
    React.useEffect(() => setLoading(fetchStatus !== LoaderStatus.Ready), [fetchStatus]);

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.vertical-geometry.plan-search-legend')}
            </p>
            <div className={styles['data-products__search']}>
                <FieldLayout
                    label={t(`data-products.search.source`)}
                    value={planSources.map((source) => (
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
                />
                <div className={styles['element-list__plan-search-dropdown']}>
                    <FieldLayout
                        label={t(`data-products.search.plan`)}
                        value={
                            <Dropdown
                                qaId="data-products-search-plan"
                                value={state.plan}
                                getName={(item: GeometryPlanHeader) => getPlanFullName(item)}
                                placeholder={t('data-products.search.search')}
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
                <PrivilegeRequired privilege={DOWNLOAD_GEOMETRY}>
                    <a
                        qa-id="vertical-geometry-csv-download"
                        {...(state.plan && {
                            href: getGeometryPlanVerticalGeometryCsv(state.plan?.id),
                        })}>
                        <Button
                            className={styles['element-list__download-button']}
                            disabled={
                                !state.verticalGeometry || state.verticalGeometry.length === 0
                            }
                            icon={Icons.Download}>
                            {t(`data-products.search.download-csv`)}
                        </Button>
                    </a>
                </PrivilegeRequired>
            </div>
        </React.Fragment>
    );
};

export default PlanVerticalGeometrySearch;
