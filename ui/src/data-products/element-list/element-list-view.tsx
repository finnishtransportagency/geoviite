import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './element-list-view.scss';
import { Radio } from 'vayla-design-lib/radio/radio';
import { EnvRestricted } from 'environment/env-restricted';

const ElementListView = () => {
    const { t } = useTranslation();
    const [continuousGeometrySelected, setContinuousGeometrySelected] = React.useState(true);

    const handleRadioClick = () => {
        setContinuousGeometrySelected(!continuousGeometrySelected);
    };

    return (
        <EnvRestricted restrictTo={'dev'}>
            <div className={styles['element-list-view']}>
                <h2>{t('data-products.element-list.element-list-title')}</h2>
                <div>
                    <span className={styles['element-list-view__radio-layout']}>
                        <Radio onChange={handleRadioClick} checked={continuousGeometrySelected}>
                            {t('data-products.element-list.continuous-geometry')}
                        </Radio>
                        <Radio onChange={handleRadioClick} checked={!continuousGeometrySelected}>
                            {t('data-products.element-list.plan-geometry')}
                        </Radio>
                    </span>
                </div>
            </div>
        </EnvRestricted>
    );
};

export default ElementListView;
