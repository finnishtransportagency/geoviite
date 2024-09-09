import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getGeometryPlan } from 'geometry/geometry-api';
import { GeometryPlan, GeometryPlanId } from 'geometry/geometry-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { InfraModelBaseProps, InfraModelView } from 'infra-model/view/infra-model-view';
import { ValidationResponse } from 'infra-model/infra-model-slice';
import {
    getValidationIssuesForGeometryPlan,
    updateGeometryPlan,
} from 'infra-model/infra-model-api';

export type InfraModelLoaderProps = {
    setExistingInfraModel: (plan: GeometryPlan | undefined) => void;
    onValidation: (validationResponse: ValidationResponse) => void;
    setLoading: (loading: boolean) => void;
} & InfraModelBaseProps;

export const InfraModelEditLoader: React.FC<InfraModelLoaderProps> = ({ ...props }) => {
    const { id: planId } = useParams<{ id: string }>();
    const [isLoading, setIsLoading] = useState(true);
    const [initializedPlanId, setInitializedPlanId] = useState<GeometryPlanId>();

    const extraParams = props.extraInfraModelParameters;
    const overrideParams = props.overrideInfraModelParameters;

    const onValidate: () => void = async () => {
        if (planId && planId === initializedPlanId) {
            props.setLoading(true);
            await getValidationIssuesForGeometryPlan(planId, overrideParams)
                .then(props.onValidation)
                .finally(() => props.setLoading(false));
        }
    };
    // Automatically re-validate whenever the manually input data changes
    React.useEffect(() => {
        onValidate();
    }, [overrideParams, initializedPlanId]);

    const onSave: () => Promise<boolean> = async () => {
        if (planId && planId === initializedPlanId) {
            props.setSaving(true);
            return await updateGeometryPlan(planId, extraParams, overrideParams)
                .then((res) => !!res)
                .finally(() => props.setSaving(false));
        } else return false;
    };

    useEffect(() => {
        if (planId) {
            props.clearInfraModelState();
            setIsLoading(true);
            getGeometryPlan(planId).then((plan: GeometryPlan | undefined) => {
                props.setExistingInfraModel(plan);
                setIsLoading(false);
                setInitializedPlanId(planId);
            });
        }
    }, [planId]);

    return isLoading ? (
        <Spinner />
    ) : (
        <InfraModelView {...props} fileSource={'STORED'} onSave={onSave} />
    );
};
