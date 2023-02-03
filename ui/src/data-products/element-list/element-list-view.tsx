import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './element-list-view.scss';
import { Radio } from 'vayla-design-lib/radio/radio';
import { EnvRestricted } from 'environment/env-restricted';
import ContinuousGeometrySearch from 'data-products/element-list/continuous-geometry-search';
import {
    continuousGeometryReducer,
    continuousGeometryActions,
    initialContinuousSearchState,
    initialPlanGeometrySearchState,
    planSearchReducer,
    planSearchActions,
} from 'data-products/element-list/element-list-store';
import { createDelegates } from 'store/store-utils';
import PlanGeometrySearch from 'data-products/element-list/plan-geometry-search';

const ElementListView = () => {
    const { t } = useTranslation();
    const [continuousGeometrySelected, setContinuousGeometrySelected] = React.useState(true);

    const handleRadioClick = () => {
        setContinuousGeometrySelected(!continuousGeometrySelected);
    };

    const [continuousSearchState, continuousStateSearchDispatcher] = React.useReducer(
        continuousGeometryReducer,
        initialContinuousSearchState,
    );
    const continuousSearchStateActions = createDelegates(
        continuousStateSearchDispatcher,
        continuousGeometryActions,
    );

    const [planSearchState, planSearchStateDispatcher] = React.useReducer(
        planSearchReducer,
        initialPlanGeometrySearchState,
    );
    const planSearchStateActions = createDelegates(planSearchStateDispatcher, planSearchActions);

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
                {continuousGeometrySelected ? (
                    <ContinuousGeometrySearch
                        state={continuousSearchState}
                        onUpdateProp={continuousSearchStateActions.onUpdateProp}
                        onCommitField={continuousSearchStateActions.onCommitField}
                    />
                ) : (
                    <PlanGeometrySearch
                        state={planSearchState}
                        onUpdateProp={planSearchStateActions.onUpdateProp}
                    />
                )}
            </div>
        </EnvRestricted>
    );
};

export default ElementListView;
