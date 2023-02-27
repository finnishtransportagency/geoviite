import React from 'react';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { ExtraInfraModelParameters } from 'infra-model/infra-model-store';
import { EditablePlanField } from 'infra-model/view/form/infra-model-form';
import { useTranslation } from 'react-i18next';
import { planPhases } from 'utils/enum-localization-utils';
import PlanPhase from 'geoviite-design-lib/geometry-plan/plan-phase';

export type InfraModelPhaseFieldProps = {
    fieldInEdit: EditablePlanField;
    setFieldInEdit: (editablePlanField: EditablePlanField | undefined) => void;
    extraInframodelParameters: ExtraInfraModelParameters;
    changeInExtraParametersField: <
        TKey extends keyof ExtraInfraModelParameters,
        TValue extends ExtraInfraModelParameters[TKey],
    >(
        value: TValue,
        fieldName: TKey,
    ) => void;
};

export const InfraModelPhaseField: React.FC<InfraModelPhaseFieldProps> = ({
    fieldInEdit,
    setFieldInEdit,
    extraInframodelParameters,
    changeInExtraParametersField,
}: InfraModelPhaseFieldProps) => {
    const { t } = useTranslation();

    return (
        <FormgroupField
            label={t('im-form.plan-phase-field')}
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
