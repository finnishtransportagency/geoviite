import React from 'react';
import { EditablePlanField } from 'infra-model/view/form/infra-model-form';
import { ExtraInfraModelParameters } from 'infra-model/infra-model-slice';
import { useTranslation } from 'react-i18next';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import ElevationMeasurementMethod from 'geoviite-design-lib/elevation-measurement-method/elevation-measurement-method';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { elevationMeasurementMethods } from 'utils/enum-localization-utils';

export type InfraModelElevationMeasurementMethodFieldProps = {
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

export const InfraModelElevationMeasurementMethodField: React.FC<
    InfraModelElevationMeasurementMethodFieldProps
> = ({
    fieldInEdit,
    setFieldInEdit,
    extraInframodelParameters,
    changeInExtraParametersField,
}: InfraModelElevationMeasurementMethodFieldProps) => {
    const { t } = useTranslation();

    return (
        <FormgroupField
            label={t('im-form.elevation-measurement-method-field')}
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
