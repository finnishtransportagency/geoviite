import React from 'react';
import { useTranslation } from 'react-i18next';
import { GeometryPlanHeader } from 'geometry/geometry-model';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { getPlanApplicabilityTranslationKey } from 'tool-panel/geometry-plan/geometry-plan-applicability-selector';

type GeometryPlanApplicabilityProps = {
    planHeader: GeometryPlanHeader;
};
export const GeometryPlanApplicability: React.FC<GeometryPlanApplicabilityProps> = ({
    planHeader,
}) => {
    const { t } = useTranslation();

    return (
        <InfoboxField
            label={t('tool-panel.geometry-plan.applicability')}
            value={t(getPlanApplicabilityTranslationKey(planHeader.planApplicability))}
        />
    );
};
