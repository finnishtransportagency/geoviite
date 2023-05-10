import React from 'react';
import { useParams } from 'react-router-dom';
import { InfraModelBaseProps, InfraModelView } from 'infra-model/view/infra-model-view';
import {
    getValidationErrorsForVelhoDocument,
    importVelhoDocument,
} from 'infra-model/infra-model-api';
import { ValidationResponse } from 'infra-model/infra-model-slice';

export type InfraModelImportLoaderProps = InfraModelBaseProps & {
    onValidation: (validationResponse: ValidationResponse) => void;
    setLoading: (loading: boolean) => void;
};

export const InfraModelImportLoader: React.FC<InfraModelImportLoaderProps> = ({ ...props }) => {
    const { id: velhoDocId } = useParams<{ id: string }>();
    const extraParams = props.extraInfraModelParameters;
    const overrideParams = props.overrideInfraModelParameters;

    const onValidate: () => Promise<null> = async () => {
        if (velhoDocId) {
            props.setLoading(true);
            props.onValidation(
                await getValidationErrorsForVelhoDocument(velhoDocId, overrideParams),
            );
            props.setLoading(false);
        }
        return null;
    };
    // Automatically re-validate whenever the plan or manually input data changes
    React.useEffect(() => {
        onValidate();
    }, [velhoDocId, overrideParams]);

    const onSave: () => Promise<boolean> = async () => {
        if (!velhoDocId) return false;
        props.setLoading(true);
        const response = await importVelhoDocument(velhoDocId, extraParams, overrideParams);
        props.setLoading(false);
        return response != null;
    };
    return <InfraModelView {...props} onSave={onSave} onValidate={onValidate} />;
};
