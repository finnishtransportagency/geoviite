import React from 'react';
import { PlanDecisionPhase } from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type DecisionPhaseProps = {
    decision: PlanDecisionPhase | undefined;
};

function getTranslationKey(decision: PlanDecisionPhase | undefined) {
    switch (decision) {
        case 'APPROVED_PLAN':
        case 'UNDER_CONSTRUCTION':
        case 'IN_USE':
            return decision;
        case undefined:
            return 'UNKNOWN';
        default:
            return exhaustiveMatchingGuard(decision);
    }
}

const DecisionPhase: React.FC<DecisionPhaseProps> = ({ decision }: DecisionPhaseProps) => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            {t(`enum.PlanDecisionPhase.${getTranslationKey(decision)}`)}
        </React.Fragment>
    );
};

export default DecisionPhase;
