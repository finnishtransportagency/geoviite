import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { Radio } from 'vayla-design-lib/radio/radio';
import LocationTrackElementListingSearch from 'data-products/element-list/location-track-element-listing-search';
import { createDelegates } from 'store/store-utils';
import PlanGeometryElementListingSearch from 'data-products/element-list/plan-geometry-element-listing-search';
import { ElementTable } from 'data-products/element-list/element-table';
import { useDataProductsAppSelector } from 'store/hooks';
import { dataProductsActions, SelectedGeometrySearch } from 'data-products/data-products-slice';
import { EntireRailNetworkElementListing } from 'data-products/element-list/entire-rail-network-element-listing';

const ElementListView = () => {
    const { t } = useTranslation();
    const [loading, setLoading] = React.useState(false);
    const dataProductsDelegates = React.useMemo(() => createDelegates(dataProductsActions), []);
    const state = useDataProductsAppSelector((state) => state.elementList);

    const handleRadioClick = (selected: SelectedGeometrySearch) => {
        dataProductsDelegates.setSelectedElementListSearch(selected);
    };

    return (
        <div className={styles['data-product-view']}>
            <div className={styles['data-product-view__header-container']}>
                <h2>{t('data-products.element-list.element-list-title')}</h2>
                <div>
                    <span className={styles['data-product-view__radio-layout']}>
                        <Radio
                            onChange={() => handleRadioClick('LOCATION_TRACK')}
                            checked={state.selectedSearch === 'LOCATION_TRACK'}>
                            {t('data-products.element-list.location-track-geometry')}
                        </Radio>
                        <Radio
                            onChange={() => handleRadioClick('PLAN')}
                            checked={state.selectedSearch === 'PLAN'}>
                            {t('data-products.element-list.plan-geometry')}
                        </Radio>
                        <Radio
                            onChange={() => handleRadioClick('ENTIRE_RAIL_NETWORK')}
                            checked={state.selectedSearch === 'ENTIRE_RAIL_NETWORK'}>
                            {t('data-products.element-list.entire-rail-network-geometry')}
                        </Radio>
                    </span>
                </div>
                {state.selectedSearch === 'LOCATION_TRACK' && (
                    <LocationTrackElementListingSearch
                        state={state.locationTrackSearch}
                        onUpdateProp={dataProductsDelegates.onUpdateLocationTrackSearchProp}
                        onCommitField={dataProductsDelegates.onCommitLocationTrackSearchField}
                        setElements={dataProductsDelegates.onSetLocationTrackElements}
                        setLoading={setLoading}
                    />
                )}
                {state.selectedSearch === 'PLAN' && (
                    <PlanGeometryElementListingSearch
                        state={state.planSearch}
                        onUpdateProp={dataProductsDelegates.onUpdatePlanSearchProp}
                        setElements={dataProductsDelegates.onSetPlanElements}
                        setLoading={setLoading}
                    />
                )}
                {state.selectedSearch === 'ENTIRE_RAIL_NETWORK' && (
                    <EntireRailNetworkElementListing />
                )}
            </div>
            {state.selectedSearch !== 'ENTIRE_RAIL_NETWORK' && (
                <ElementTable
                    elements={
                        state.selectedSearch === 'LOCATION_TRACK'
                            ? state.locationTrackSearch.elements
                            : state.planSearch.elements
                    }
                    showLocationTrackName={state.selectedSearch === 'LOCATION_TRACK'}
                    isLoading={loading}
                />
            )}
        </div>
    );
};

export default ElementListView;
