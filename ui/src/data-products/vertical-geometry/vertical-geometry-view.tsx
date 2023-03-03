import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Radio } from 'vayla-design-lib/radio/radio';
import styles from 'data-products/data-product-view.scss';
import PlanVerticalGeometrySearch from 'data-products/vertical-geometry/plan-vertical-geometry-search';
import LocationTrackVerticalGeometrySearch from 'data-products/vertical-geometry/location-track-vertical-geometry-search';
import { useDataProductsAppDispatch, useDataProductsAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { dataProductsActions } from 'data-products/element-list/element-list-store';

const VerticalGeometryView = () => {
    const rootDispatch = useDataProductsAppDispatch();
    const dataProductsDelegates = createDelegates(rootDispatch, dataProductsActions);
    const state = useDataProductsAppSelector((state) => state.dataProducts.verticalGeometry);

    const { t } = useTranslation();
    const locationTrackSelected = state.selectedSearch === 'LOCATION_TRACK';

    const handleRadioClick = () => {
        dataProductsDelegates.setSelectedVerticalGeometrySearch(
            locationTrackSelected ? 'PLAN' : 'LOCATION_TRACK',
        );
    };

    return (
        <div className={styles['data-product-view']}>
            <div className={styles['data-product-view__header-container']}>
                <h2>{t('data-products.vertical-geometry.vertical-geometry-title')}</h2>
                <div>
                    <span className={styles['data-product-view__radio-layout']}>
                        <Radio onChange={handleRadioClick} checked={locationTrackSelected}>
                            {t('data-products.vertical-geometry.location-track-vertical-geometry')}
                        </Radio>
                        <Radio onChange={handleRadioClick} checked={!locationTrackSelected}>
                            {t('data-products.vertical-geometry.plan-vertical-geometry')}
                        </Radio>
                    </span>
                </div>
                {locationTrackSelected ? (
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
                    />
                ) : (
                    <PlanVerticalGeometrySearch
                        state={state.planSearch}
                        onUpdateProp={dataProductsDelegates.onUpdatePlanVerticalGeometrySearchProp}
                        setVerticalGeometry={dataProductsDelegates.onSetPlanVerticalGeometry}
                    />
                )}
            </div>
        </div>
    );
};

export default VerticalGeometryView;
