import React from 'react';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { useTranslation } from 'react-i18next';
import { planPhases } from 'utils/enum-localization-utils';
import PlanPhase from 'geoviite-design-lib/geometry-plan/plan-phase';
import { InfraModelExtraParameterFieldProps } from 'infra-model/view/form/fields/infra-model-field-model';

export const InfraModelPhaseField: React.FC<InfraModelExtraParameterFieldProps> = ({
    fieldInEdit,
    setFieldInEdit,
    extraInframodelParameters,
    changeInExtraParametersField,
}: InfraModelExtraParameterFieldProps) => {
    const { t } = useTranslation();

    return (
        <FormgroupField
            label={t('im-form.plan-phase-field')}
            qaId="plan-phase-im-field"
            inEditMode={fieldInEdit === 'planPhase'}
            onEdit={() => setFieldInEdit('planPhase')}
            onClose={() => setFieldInEdit(undefined)}>
            {fieldInEdit !== 'planPhase' ? (
                extraInframodelParameters.planPhase ? (
                    <PlanPhase phase={extraInframodelParameters.planPhase} />
                ) : (
                    t('im-form.information-missing')
                )
            ) : (
                <FieldLayout
                    value={
                        <Dropdown
                            placeholder={t('im-form.information-missing')}
                            wide
                            wideList
                            value={extraInframodelParameters.planPhase}
                            options={planPhases}
                            unselectText={t('im-form.information-missing')}
                            canUnselect
                            onChange={(phase) => changeInExtraParametersField(phase, 'planPhase')}
                        />
                    }
                />
            )}
        </FormgroupField>
    );
};
