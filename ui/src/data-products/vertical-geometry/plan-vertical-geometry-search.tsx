import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/vertical-geometry/vertical-geometry-view.scss';

export const PlanVerticalGeometrySearch: React.FC = () => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <p className={styles['vertical-geometry__search-legend']}>
                {t('data-products.vertical-geometry.plan-search-legend')}
            </p>
        </React.Fragment>
    );
};

export default PlanVerticalGeometrySearch;
