import React from 'react';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import {
    InfraModelParametersProp,
    OverrideInfraModelParameters,
} from 'infra-model/infra-model-slice';
import { EditablePlanField } from 'infra-model/view/form/infra-model-form';
import { useTranslation } from 'react-i18next';
import { verticalCoordinateSystems } from 'utils/enum-localization-utils';
import { VerticalCoordinateSystem } from 'common/common-model';

export type InfraModelVerticalCoordinateInfoboxFieldProps = {
    fieldInEdit: EditablePlanField;
    setFieldInEdit: (editablePlanField: EditablePlanField | undefined) => void;
    value: VerticalCoordinateSystem;
    changeInOverrideParametersField: <
        TKey extends keyof OverrideInfraModelParameters,
        TValue extends OverrideInfraModelParameters[TKey],
    >(
        value: TValue,
        fieldName: TKey,
    ) => void;
    getVisibleErrorsByProp?: (prop: InfraModelParametersProp) => string[];
};

export const InfraModelVerticalCoordinateInfoboxField: React.FC<
    InfraModelVerticalCoordinateInfoboxFieldProps
> = ({
    fieldInEdit,
    setFieldInEdit,
    value,
    changeInOverrideParametersField,
    getVisibleErrorsByProp,
}: InfraModelVerticalCoordinateInfoboxFieldProps) => {
    const { t } = useTranslation();

    return (
        <FormgroupField
            label={t('im-form.vertical-coordinate-system-field')}
            qaId="vertical-coordinate-system-im-field"
            inEditMode={fieldInEdit === 'heightSystem'}
            onEdit={() => setFieldInEdit('heightSystem')}
            onClose={() => setFieldInEdit(undefined)}>
            {fieldInEdit !== 'heightSystem' ? (
                value ? (
                    value
                ) : (
                    t('im-form.information-missing')
                )
            ) : (
                <FieldLayout
                    value={
                        <Dropdown
                            wide
                            placeholder={t('im-form.vertical-coordinate-system-field')}
                            value={value}
                            options={verticalCoordinateSystems}
                            canUnselect
                            onChange={(verticalCoordinateSystem) =>
                                changeInOverrideParametersField(
                                    verticalCoordinateSystem,
                                    'verticalCoordinateSystem',
                                )
                            }
                        />
                    }
                    errors={
                        getVisibleErrorsByProp && getVisibleErrorsByProp('verticalCoordinateSystem')
                    }
                />
            )}
        </FormgroupField>
    );
};
