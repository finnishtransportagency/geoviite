import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Radio } from 'vayla-design-lib/radio/radio';
import styles from 'data-products/data-product-view.scss';
import PlanVerticalGeometrySearch from 'data-products/vertical-geometry/plan-vertical-geometry-search';
import LocationTrackVerticalGeometrySearch from 'data-products/vertical-geometry/location-track-vertical-geometry-search';
import { useDataProductsAppDispatch, useDataProductsAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { dataProductsActions } from 'data-products/data-products-store';
import { VerticalGeometryTable } from 'data-products/vertical-geometry/vertical-geometry-table';
import { VerticalGeometry } from 'geometry/geometry-model';

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

    // TODO This is for illustrative purposes only. Remove this when backend APIs have been implemented
    const testData: VerticalGeometry[] = [
        {
            id: '2',
            planId: 'INT_1',
            planFileName: 'asd.xml',
            locationTrack: 'asd',
            curveStart: {
                address: {
                    kmNumber: '0001',
                    meters: 0.0,
                },
                height: 64,
                angle: 0.03,
            },
            curveEnd: {
                address: {
                    kmNumber: '0001',
                    meters: 0.0,
                },
                height: 64,
                angle: 0.03,
            },
            pviAddress: {
                kmNumber: '0001',
                meters: 0.0,
            },
            pviHeight: 6.3,
            radius: 2500,
            tangent: 3,
            linearSectionBackwards: {
                length: 100,
                linearSection: 99,
            },
            linearSectionForwards: {
                length: 100,
                linearSection: 99,
            },
            station: {
                start: 100,
                end: 100,
                pvi: 100,
            },
        },
    ];

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
            <VerticalGeometryTable verticalGeometry={testData} />
        </div>
    );
};

export default VerticalGeometryView;
