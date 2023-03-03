import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { Radio } from 'vayla-design-lib/radio/radio';
import ContinuousGeometrySearch from 'data-products/element-list/continuous-geometry-search';
import { dataProductsActions } from 'data-products/element-list/element-list-store';
import { createDelegates } from 'store/store-utils';
import PlanGeometrySearch from 'data-products/element-list/plan-geometry-search';
import { ElementTable } from 'data-products/element-list/element-table';
import { useDataProductsAppDispatch, useDataProductsAppSelector } from 'store/hooks';

const ElementListView = () => {
    const rootDispatch = useDataProductsAppDispatch();
    const dataProductsDelegates = createDelegates(rootDispatch, dataProductsActions);
    const state = useDataProductsAppSelector((state) => state.dataProducts.elementList);
    const { t } = useTranslation();
    const continuousGeometrySelected = state.selectedSearch === 'LOCATION_TRACK';

    const handleRadioClick = () => {
        dataProductsDelegates.setSelectedElementListSearch(
            continuousGeometrySelected ? 'PLAN' : 'LOCATION_TRACK',
        );
    };

    return (
        <div className={styles['data-product-view']}>
            <div className={styles['data-product-view__header-container']}>
                <h2>{t('data-products.element-list.element-list-title')}</h2>
                <div>
                    <span className={styles['data-product-view__radio-layout']}>
                        <Radio onChange={handleRadioClick} checked={continuousGeometrySelected}>
                            {t('data-products.element-list.location-track-geometry')}
                        </Radio>
                        <Radio onChange={handleRadioClick} checked={!continuousGeometrySelected}>
                            {t('data-products.element-list.plan-geometry')}
                        </Radio>
                    </span>
                </div>
                {continuousGeometrySelected ? (
                    <ContinuousGeometrySearch
                        state={state.locationTrackSearch}
                        onUpdateProp={dataProductsDelegates.onUpdateLocationTrackSearchProp}
                        onCommitField={dataProductsDelegates.onCommitLocationTrackSearchField}
                        setElements={dataProductsDelegates.onSetLocationTrackElements}
                    />
                ) : (
                    <PlanGeometrySearch
                        state={state.planSearch}
                        onUpdateProp={dataProductsDelegates.onUpdatePlanSearchProp}
                        setElements={dataProductsDelegates.onSetPlanElements}
                    />
                )}
            </div>
            <ElementTable
                elements={
                    continuousGeometrySelected
                        ? state.locationTrackSearch.elements
                        : state.planSearch.elements
                }
                showLocationTrackName={continuousGeometrySelected}
            />
        </div>
    );
};

export default ElementListView;
