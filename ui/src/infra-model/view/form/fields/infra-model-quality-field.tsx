import React from 'react';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { useTranslation } from 'react-i18next';
import { planQualities } from 'utils/enum-localization-utils';
import PlanQualityView from 'geoviite-design-lib/geometry-plan/plan-quality';
import { InfraModelExtraParameterFieldProps } from 'infra-model/view/form/fields/infra-model-field-model';

export const InfraModelQualityField: React.FC<InfraModelExtraParameterFieldProps> = ({
    fieldInEdit,
    setFieldInEdit,
    extraInframodelParameters,
    changeInExtraParametersField,
    errors,
}: InfraModelExtraParameterFieldProps) => {
    const { t } = useTranslation();

    return (
        <FormgroupField
            label={t('im-form.quality-field')}
            qaId="quality-im-field"
            inEditMode={fieldInEdit === 'quality'}
            onEdit={() => setFieldInEdit('quality')}
            onClose={() => setFieldInEdit(undefined)}
            errors={errors}>
            {fieldInEdit !== 'quality' ? (
                <PlanQualityView quality={extraInframodelParameters.quality} />
            ) : (
                <FieldLayout
                    value={
                        <Dropdown
                            wide
                            value={extraInframodelParameters.quality}
                            options={planQualities}
                            onChange={(quality) =>
                                changeInExtraParametersField(quality, 'quality')
                            }
                        />
                    }
                />
            )}
        </FormgroupField>
    );
};
