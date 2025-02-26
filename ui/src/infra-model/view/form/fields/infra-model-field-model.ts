import { EditablePlanField } from 'infra-model/view/form/infra-model-form';
import { ExtraInfraModelParameters } from 'infra-model/infra-model-slice';

export type InfraModelExtraParameterFieldProps = {
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
