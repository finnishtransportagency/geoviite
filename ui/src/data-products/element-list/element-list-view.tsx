import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './element-list-view.scss';
import { Radio } from 'vayla-design-lib/radio/radio';
import { EnvRestricted } from 'environment/env-restricted';

const ElementListView = () => {
    const { t } = useTranslation();
    //const [continuousGeometrySelected, setContinuousGeometrySelected] = React.useState(false);
    //const [planGeometrySelected, setPlanGeometrySelected] = React.useState(false);

    return (
        <EnvRestricted restrictTo={'dev'}>
            <div className={styles['element-list-view']}>
                <h3>{t('data-products.element-list.element-list-title')}</h3>
                <div>
                    <Radio>{t('data-products.element-list.continuous-geometry')}</Radio>
                    <Radio>{t('data-products.element-list.plan-geometry')}</Radio>
                </div>
            </div>
        </EnvRestricted>
    );
};

export default ElementListView;
