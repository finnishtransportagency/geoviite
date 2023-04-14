import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { Radio } from 'vayla-design-lib/radio/radio';
import LocationTrackElementListingSearch from 'data-products/element-list/location-track-element-listing-search';
import { dataProductsActions } from 'data-products/data-products-store';
import { createDelegates } from 'store/store-utils';
import PlanGeometryElementListingSearch from 'data-products/element-list/plan-geometry-element-listing-search';
import { ElementTable } from 'data-products/element-list/element-table';
import { useDataProductsAppDispatch, useDataProductsAppSelector } from 'store/hooks';

const ElementListView = () => {
    const { t } = useTranslation();
    const rootDispatch = useDataProductsAppDispatch();
    const dataProductsDelegates = createDelegates(rootDispatch, dataProductsActions);
    const state = useDataProductsAppSelector((state) => state.dataProducts.elementList);
    const [loading, setLoading] = React.useState(false);
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
                    <LocationTrackElementListingSearch
                        state={state.locationTrackSearch}
                        onUpdateProp={dataProductsDelegates.onUpdateLocationTrackSearchProp}
                        onCommitField={dataProductsDelegates.onCommitLocationTrackSearchField}
                        setElements={dataProductsDelegates.onSetLocationTrackElements}
                        setLoading={setLoading}
                    />
                ) : (
                    <PlanGeometryElementListingSearch
                        state={state.planSearch}
                        onUpdateProp={dataProductsDelegates.onUpdatePlanSearchProp}
                        setElements={dataProductsDelegates.onSetPlanElements}
                        setLoading={setLoading}
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
                isLoading={loading}
            />
        </div>
    );
};

export default ElementListView;
