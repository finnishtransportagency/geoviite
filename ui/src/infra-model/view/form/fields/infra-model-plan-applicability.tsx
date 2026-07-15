import React from 'react';
import { useTranslation } from 'react-i18next';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import { PlanApplicability } from 'geometry/geometry-model';

type InfraModelPlanApplicabilityFieldProps = {
    planApplicability: PlanApplicability | undefined;
};

export const InfraModelPlanApplicabilityField: React.FC<InfraModelPlanApplicabilityFieldProps> = ({
    planApplicability,
}: InfraModelPlanApplicabilityFieldProps) => {
    const { t } = useTranslation();

    return (
        <FormgroupField label={t('im-form.plan-applicability-field')} qaId="plan-applicability-im-field">
            {planApplicability
                ? t(`enum.PlanApplicability.${planApplicability}`)
                : t('im-form.information-missing')}
        </FormgroupField>
    );
};
