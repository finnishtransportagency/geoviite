import React from 'react';
import { useTranslation } from 'react-i18next';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import ElevationMeasurementMethod from 'geoviite-design-lib/elevation-measurement-method/elevation-measurement-method';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { elevationMeasurementMethods } from 'utils/enum-localization-utils';
import { InfraModelExtraParameterFieldProps } from 'infra-model/view/form/fields/infra-model-field-model';

export const InfraModelElevationMeasurementMethodField: React.FC<
    InfraModelExtraParameterFieldProps
> = ({
    fieldInEdit,
    setFieldInEdit,
    extraInframodelParameters,
    changeInExtraParametersField,
}: InfraModelExtraParameterFieldProps) => {
    const { t } = useTranslation();

    return (
        <FormgroupField
            label={t('im-form.elevation-measurement-method-field')}
            qaId="elevation-measurement-method-im-field"
            inEditMode={fieldInEdit === 'elevationMeasurementMethod'}
            onEdit={() => setFieldInEdit('elevationMeasurementMethod')}
            onClose={() => setFieldInEdit(undefined)}>
            {fieldInEdit !== 'elevationMeasurementMethod' ? (
                extraInframodelParameters.elevationMeasurementMethod ? (
                    <ElevationMeasurementMethod
                        method={extraInframodelParameters.elevationMeasurementMethod}
                    />
                ) : (
                    t('im-form.information-missing')
                )
            ) : (
                <FieldLayout
                    value={
                        <Dropdown
                            wide
                            placeholder={t('im-form.information-missing')}
                            value={extraInframodelParameters.elevationMeasurementMethod}
                            options={elevationMeasurementMethods}
                            unselectText={t('im-form.information-missing')}
                            canUnselect={true}
                            onChange={(elevationMeasurementMethod) =>
                                changeInExtraParametersField(
                                    elevationMeasurementMethod,
                                    'elevationMeasurementMethod',
                                )
                            }
                        />
                    }
                />
            )}
        </FormgroupField>
    );
};
