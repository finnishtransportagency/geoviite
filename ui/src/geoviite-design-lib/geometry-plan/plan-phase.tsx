import React from 'react';
import { PlanPhase as PlanPhaseModel } from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type PlanPhaseProps = {
    phase: PlanPhaseModel | null;
};

function getTranslationKey(phase: PlanPhaseModel | null) {
    switch (phase) {
        case 'RAILWAY_PLAN':
        case 'RAILWAY_CONSTRUCTION_PLAN':
        case 'RENOVATION_PLAN':
        case 'ENHANCED_RENOVATION_PLAN':
        case 'MAINTENANCE':
        case 'NEW_INVESTMENT':
        case 'REMOVED_FROM_USE':
            return phase;
        case null:
            return 'UNKNOWN';
        default:
            return exhaustiveMatchingGuard(phase);
    }
}

const PlanPhase: React.FC<PlanPhaseProps> = ({ phase }: PlanPhaseProps) => {
    const { t } = useTranslation();

    return <React.Fragment>{t(`enum.plan-phase.${getTranslationKey(phase)}`)}</React.Fragment>;
};

export default PlanPhase;
