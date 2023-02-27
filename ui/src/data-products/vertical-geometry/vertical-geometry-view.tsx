import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Radio } from 'vayla-design-lib/radio/radio';
import styles from 'data-products/vertical-geometry/vertical-geometry-view.scss';
import PlanVerticalGeometrySearch from 'data-products/vertical-geometry/plan-vertical-geometry-search';
import LocationTrackVerticalGeometrySearch from 'data-products/vertical-geometry/location-track-vertical-geometry-search';

const VerticalGeometryView = () => {
    const { t } = useTranslation();
    const [locationTrackSelected, setLocationTrackSelected] = React.useState(true);

    const handleRadioClick = () => {
        setLocationTrackSelected(!locationTrackSelected);
    };

    return (
        <div className={styles['vertical-geometry-view']}>
            <div className={styles['vertical-geometry-view__header-container']}>
                <h2>{t('data-products.vertical-geometry.vertical-geometry-title')}</h2>
                <div>
                    <span className={styles['vertical-geometry-view__radio-layout']}>
                        <Radio onChange={handleRadioClick} checked={locationTrackSelected}>
                            {t('data-products.vertical-geometry.location-track-vertical-geometry')}
                        </Radio>
                        <Radio onChange={handleRadioClick} checked={!locationTrackSelected}>
                            {t('data-products.vertical-geometry.plan-vertical-geometry')}
                        </Radio>
                    </span>
                </div>
                {locationTrackSelected ? (
                    <LocationTrackVerticalGeometrySearch />
                ) : (
                    <PlanVerticalGeometrySearch />
                )}
            </div>
        </div>
    );
};

export default VerticalGeometryView;
