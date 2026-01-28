import React from 'react';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { useTranslation } from 'react-i18next';
import { measurementMethods } from 'utils/enum-localization-utils';
import MeasurementMethod from 'geoviite-design-lib/measurement-method/measurement-method';
import { InfraModelExtraParameterFieldProps } from 'infra-model/view/form/fields/infra-model-field-model';

export const InfraModelMeasurementMethodField: React.FC<InfraModelExtraParameterFieldProps> = ({
    fieldInEdit,
    setFieldInEdit,
    extraInframodelParameters,
    changeInExtraParametersField,
}: InfraModelExtraParameterFieldProps) => {
    const { t } = useTranslation();

    return (
        <FormgroupField
            label={t('im-form.measurement-method-field')}
            qaId="measurement-method-im-field"
            inEditMode={fieldInEdit === 'measurementMethod'}
            onEdit={() => setFieldInEdit('measurementMethod')}
            onClose={() => setFieldInEdit(undefined)}>
            {fieldInEdit !== 'measurementMethod' ? (
                extraInframodelParameters.measurementMethod ? (
                    <MeasurementMethod method={extraInframodelParameters.measurementMethod} />
                ) : (
                    t('im-form.information-missing')
                )
            ) : (
                <FieldLayout
                    value={
                        <Dropdown
                            wide
                            placeholder={t('im-form.information-missing')}
                            value={extraInframodelParameters.measurementMethod}
                            options={measurementMethods}
                            unselectText={t('im-form.information-missing')}
                            canUnselect
                            onChange={(measurementMethod) =>
                                changeInExtraParametersField(measurementMethod, 'measurementMethod')
                            }
                        />
                    }
                />
            )}
        </FormgroupField>
    );
};
