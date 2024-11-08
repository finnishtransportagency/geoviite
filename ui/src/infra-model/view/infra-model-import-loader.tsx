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
};

export const InfraModelImportLoader: React.FC<InfraModelImportLoaderProps> = ({ ...props }) => {
    const { id: pvDocumentId } = useParams<{ id: string }>();
    const [initPVDocumentId, setInitPVDocumentId] = useState<PVDocumentId>();
    const extraParams = props.extraInfraModelParameters;
    const overrideParams = props.overrideInfraModelParameters;

    const onInit: () => void = async () => {
        if (pvDocumentId) {
            props.clearInfraModelState();
            props.setLoading(true);
            await getValidationIssuesForPVDocument(pvDocumentId)
                .then((validation) => {
                    props.setExistingInfraModel(validation.geometryPlan);
                    props.onValidation(validation);
                    setInitPVDocumentId(pvDocumentId);
                })
                .finally(() => props.setLoading(false));
        }
    };
    const onValidate: () => void = async () => {
        if (pvDocumentId && pvDocumentId == initPVDocumentId) {
            props.setLoading(true);
            await getValidationIssuesForPVDocument(pvDocumentId, overrideParams)
                .then(props.onValidation)
                .finally(() => props.setLoading(false));
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
        props.setSaving(true);
        return await importPVDocument(pvDocumentId, extraParams, overrideParams)
            .then((response) => response !== undefined)
            .finally(() => props.setSaving(false));
    };
    return <InfraModelView {...props} fileSource={'PV_IMPORT'} onSave={onSave} />;
};
