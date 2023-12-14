import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Radio } from 'vayla-design-lib/radio/radio';
import styles from 'data-products/data-product-view.scss';
import PlanVerticalGeometrySearch from 'data-products/vertical-geometry/plan-vertical-geometry-search';
import LocationTrackVerticalGeometrySearch from 'data-products/vertical-geometry/location-track-vertical-geometry-search';
import { useDataProductsAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import VerticalGeometryTable from 'data-products/vertical-geometry/vertical-geometry-table';
import { dataProductsActions, SelectedGeometrySearch } from 'data-products/data-products-slice';
import { EntireRailNetworkVerticalGeometryListing } from 'data-products/vertical-geometry/entire-rail-network-vertical-geometry-listing';
import { PrivilegeRequired } from 'user/privilege-required';

const VerticalGeometryView = () => {
    const dataProductsDelegates = React.useMemo(() => createDelegates(dataProductsActions), []);
    const state = useDataProductsAppSelector((state) => state.verticalGeometry);
    const [loading, setLoading] = React.useState(false);

    const { t } = useTranslation();

    const handleRadioClick = (selected: SelectedGeometrySearch) => {
        dataProductsDelegates.setSelectedVerticalGeometrySearch(selected);
    };

    return (
        <div className={styles['data-product-view']}>
            <div className={styles['data-product-view__header-container']}>
                <h2>{t('data-products.vertical-geometry.vertical-geometry-title')}</h2>
                <div>
                    <span className={styles['data-product-view__radio-layout']}>
                        <Radio
                            onChange={() => handleRadioClick('LOCATION_TRACK')}
                            checked={state.selectedSearch === 'LOCATION_TRACK'}
                            qaId="select-layout-geometry">
                            {t('data-products.vertical-geometry.location-track-vertical-geometry')}
                        </Radio>
                        <Radio
                            onChange={() => handleRadioClick('PLAN')}
                            checked={state.selectedSearch === 'PLAN'}
                            qaId="select-plan-geometry">
                            {t('data-products.vertical-geometry.plan-vertical-geometry')}
                        </Radio>
                        <PrivilegeRequired privilege="dataproduct-download">
                            <Radio
                                onChange={() => handleRadioClick('ENTIRE_RAIL_NETWORK')}
                                checked={state.selectedSearch === 'ENTIRE_RAIL_NETWORK'}
                                qaId="select-entire-rail-network">
                                {t(
                                    'data-products.vertical-geometry.entire-rail-network-vertical-geometry',
                                )}
                            </Radio>
                        </PrivilegeRequired>
                    </span>
                </div>
                {state.selectedSearch === 'LOCATION_TRACK' && (
                    <LocationTrackVerticalGeometrySearch
                        state={state.locationTrackSearch}
                        onUpdateProp={
                            dataProductsDelegates.onUpdateVerticalGeometryLocationTrackSearchProp
                        }
                        onCommitField={
                            dataProductsDelegates.onCommitVerticalGeometryLocationTrackSearchField
                        }
                        setVerticalGeometry={
                            dataProductsDelegates.onSetLocationTrackVerticalGeometry
                        }
                        setLoading={setLoading}
                    />
                )}
                {state.selectedSearch === 'PLAN' && (
                    <PlanVerticalGeometrySearch
                        state={state.planSearch}
                        onUpdateProp={dataProductsDelegates.onUpdatePlanVerticalGeometrySearchProp}
                        setVerticalGeometry={dataProductsDelegates.onSetPlanVerticalGeometry}
                        setLoading={setLoading}
                    />
                )}
                {state.selectedSearch === 'ENTIRE_RAIL_NETWORK' && (
                    <EntireRailNetworkVerticalGeometryListing />
                )}
            </div>
            {state.selectedSearch !== 'ENTIRE_RAIL_NETWORK' && (
                <VerticalGeometryTable
                    verticalGeometry={
                        state.selectedSearch === 'LOCATION_TRACK'
                            ? state.locationTrackSearch.verticalGeometry
                            : state.planSearch.verticalGeometry
                    }
                    showLocationTrack={state.selectedSearch === 'LOCATION_TRACK'}
                    isLoading={loading}
                />
            )}
        </div>
    );
};

export default VerticalGeometryView;
