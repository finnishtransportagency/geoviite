import React from 'react';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { useTranslation } from 'react-i18next';
import { planDecisionPhases } from 'utils/enum-localization-utils';
import DecisionPhase from 'geoviite-design-lib/geometry-plan/plan-decision-phase';
import { InfraModelExtraParameterFieldProps } from 'infra-model/view/form/fields/infra-model-field-model';

export const InfraModelDecisionPhaseField: React.FC<InfraModelExtraParameterFieldProps> = ({
    fieldInEdit,
    setFieldInEdit,
    extraInframodelParameters,
    changeInExtraParametersField,
}: InfraModelExtraParameterFieldProps) => {
    const { t } = useTranslation();

    return (
        <FormgroupField
            label={t('im-form.decision-phase-field')}
            qaId="decision-phase-im-field"
            inEditMode={fieldInEdit === 'planDecisionPhase'}
            onEdit={() => setFieldInEdit('planDecisionPhase')}
            onClose={() => setFieldInEdit(undefined)}>
            {fieldInEdit !== 'planDecisionPhase' ? (
                extraInframodelParameters.decisionPhase ? (
                    <DecisionPhase decision={extraInframodelParameters.decisionPhase} />
                ) : (
                    t('im-form.information-missing')
                )
            ) : (
                <FieldLayout
                    value={
                        <Dropdown
                            placeholder={t('im-form.information-missing')}
                            value={extraInframodelParameters.decisionPhase}
                            options={planDecisionPhases}
                            unselectText={t('im-form.information-missing')}
                            canUnselect
                            onChange={(decision) =>
                                changeInExtraParametersField(decision, 'decisionPhase')
                            }
                        />
                    }
                />
            )}
        </FormgroupField>
    );
};
