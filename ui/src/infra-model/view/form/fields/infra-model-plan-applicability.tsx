import React from 'react';
import { useTranslation } from 'react-i18next';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { planApplicabilities } from 'utils/enum-localization-utils';
import { InfraModelExtraParameterFieldProps } from 'infra-model/view/form/fields/infra-model-field-model';

export const InfraModelPlanApplicabilityField: React.FC<InfraModelExtraParameterFieldProps> = ({
    fieldInEdit,
    setFieldInEdit,
    extraInframodelParameters,
    changeInExtraParametersField,
}: InfraModelExtraParameterFieldProps) => {
    const { t } = useTranslation();

    return (
        <FormgroupField
            label={t('im-form.plan-applicability-field')}
            qaId="plan-applicability-im-field"
            inEditMode={fieldInEdit === 'planApplicability'}
            onEdit={() => setFieldInEdit('planApplicability')}
            onClose={() => setFieldInEdit(undefined)}>
            {fieldInEdit !== 'planApplicability' ? (
                extraInframodelParameters.planApplicability ? (
                    t(`enum.PlanApplicability.${extraInframodelParameters.planApplicability}`)
                ) : (
                    t('im-form.information-missing')
                )
            ) : (
                <FieldLayout
                    value={
                        <Dropdown
                            wide
                            placeholder={t('im-form.information-missing')}
                            value={extraInframodelParameters.planApplicability}
                            options={planApplicabilities}
                            unselectText={t('im-form.information-missing')}
                            canUnselect={true}
                            onChange={(planApplicability) =>
                                changeInExtraParametersField(planApplicability, 'planApplicability')
                            }
                        />
                    }
                />
            )}
        </FormgroupField>
    );
};
