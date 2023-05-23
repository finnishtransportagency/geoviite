import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { InfraModelBaseProps, InfraModelView } from 'infra-model/view/infra-model-view';
import {
    getValidationErrorsForVelhoDocument,
    importVelhoDocument,
} from 'infra-model/infra-model-api';
import { ValidationResponse } from 'infra-model/infra-model-slice';
import { PVDocumentId } from 'infra-model/velho/velho-model';
import { GeometryPlan } from 'geometry/geometry-model';

export type InfraModelImportLoaderProps = InfraModelBaseProps & {
    setExistingInfraModel: (plan: GeometryPlan | null) => void;
    onValidation: (validationResponse: ValidationResponse) => void;
    setLoading: (loading: boolean) => void;
};

export const InfraModelImportLoader: React.FC<InfraModelImportLoaderProps> = ({ ...props }) => {
    const { id: velhoDocId } = useParams<{ id: string }>();
    const [initializedDocId, setInitializedDocId] = useState<PVDocumentId | null>(null);
    const extraParams = props.extraInfraModelParameters;
    const overrideParams = props.overrideInfraModelParameters;

    const onInit: () => void = async () => {
        if (velhoDocId) {
            props.setLoading(true);
            const validation = await getValidationErrorsForVelhoDocument(velhoDocId);
            props.setExistingInfraModel(validation.geometryPlan);
            props.onValidation(validation);
            setInitializedDocId(velhoDocId);
            props.setLoading(false);
        }
    };
    const onValidate: () => void = async () => {
        if (velhoDocId && velhoDocId == initializedDocId) {
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
    }, [overrideParams]);

    useEffect(() => {
        onInit();
    }, [velhoDocId]);

    const onSave: () => Promise<boolean> = async () => {
        if (!velhoDocId) return false;
        props.setLoading(true);
        const response = await importVelhoDocument(velhoDocId, extraParams, overrideParams);
        props.setLoading(false);
        return response != null;
    };
    return <InfraModelView {...props} onSave={onSave} onValidate={onValidate} />;
};
