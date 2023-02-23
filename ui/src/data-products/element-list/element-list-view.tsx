import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './element-list-view.scss';
import { Radio } from 'vayla-design-lib/radio/radio';
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
import { ElementTable } from 'data-products/element-list/element-table';

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
        <div className={styles['element-list-view']}>
            <div className={styles['element-list-view__header-container']}>
                <h2>{t('data-products.element-list.element-list-title')}</h2>
                <div>
                    <span className={styles['element-list-view__radio-layout']}>
                        <Radio onChange={handleRadioClick} checked={continuousGeometrySelected}>
                            {t('data-products.element-list.location-track-geometry')}
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
                        setElements={continuousSearchStateActions.onSetElements}
                    />
                ) : (
                    <PlanGeometrySearch
                        state={planSearchState}
                        onUpdateProp={planSearchStateActions.onUpdateProp}
                        setElements={planSearchStateActions.onSetElements}
                    />
                )}
            </div>
            <ElementTable
                elements={
                    continuousGeometrySelected
                        ? continuousSearchState.elements
                        : planSearchState.elements
                }
                showLocationTrackName={continuousGeometrySelected}
            />
        </div>
    );
};

export default ElementListView;
