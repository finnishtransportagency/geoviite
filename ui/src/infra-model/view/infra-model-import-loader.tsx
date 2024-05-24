import React, { useState } from 'react';
import { useParams } from 'react-router-dom';
import { InfraModelBaseProps, InfraModelView } from 'infra-model/view/infra-model-view';
import { getValidationIssuesForPVDocument, importPVDocument } from 'infra-model/infra-model-api';
import { ValidationResponse } from 'infra-model/infra-model-slice';
import { PVDocumentId } from 'infra-model/projektivelho/pv-model';
import { GeometryPlan } from 'geometry/geometry-model';

export type InfraModelImportLoaderProps = InfraModelBaseProps & {
    setExistingInfraModel: (plan: GeometryPlan | undefined) => void;
    onValidation: (validationResponse: ValidationResponse) => void;
    setLoading: (loading: boolean) => void;
};

export const InfraModelImportLoader: React.FC<InfraModelImportLoaderProps> = ({ ...props }) => {
    const { id: pvDocumentId } = useParams<{ id: string }>();
    const [initPVDocumentId, setInitPVDocumentId] = useState<PVDocumentId>();
    const extraParams = props.extraInfraModelParameters;
    const overrideParams = props.overrideInfraModelParameters;

    const onInit: () => void = async () => {
        if (pvDocumentId) {
            props.setLoading(true);
            const validation = await getValidationIssuesForPVDocument(pvDocumentId);
            props.setExistingInfraModel(validation.geometryPlan);
            props.onValidation(validation);
            setInitPVDocumentId(pvDocumentId);
            props.setLoading(false);
        }
    };
    const onValidate: () => void = async () => {
        if (pvDocumentId && pvDocumentId == initPVDocumentId) {
            props.setLoading(true);
            props.onValidation(
                await getValidationIssuesForPVDocument(pvDocumentId, overrideParams),
            );
            props.setLoading(false);
        }
        return undefined;
    };
    // Automatically re-validate whenever the plan or manually input data changes
    React.useEffect(() => {
        onValidate();
    }, [overrideParams]);

    React.useEffect(() => {
        onInit();
    }, [pvDocumentId]);

    const onSave: () => Promise<boolean> = async () => {
        if (!pvDocumentId) return false;
        props.setLoading(true);
        const response = await importPVDocument(pvDocumentId, extraParams, overrideParams);
        props.setLoading(false);
        return !!response;
    };
    return <InfraModelView {...props} onSave={onSave} />;
};
