import React from 'react';
import { DecisionPhase as DecisionPhaseModel } from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type DecisionPhaseProps = {
    decision: DecisionPhaseModel | undefined;
};

function getTranslationKey(decision: DecisionPhaseModel | undefined) {
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
        <React.Fragment>{t(`enum.plan-decision.${getTranslationKey(decision)}`)}</React.Fragment>
    );
};

export default DecisionPhase;
