import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getGeometryPlan } from 'geometry/geometry-api';
import { GeometryPlan, GeometryPlanId } from 'geometry/geometry-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { InfraModelBaseProps, InfraModelView } from 'infra-model/view/infra-model-view';
import { ValidationResponse } from 'infra-model/infra-model-slice';
import {
    getValidationErrorsForGeometryPlan,
    updateGeometryPlan,
} from 'infra-model/infra-model-api';

export type InfraModelLoaderProps = {
    setExistingInfraModel: (plan: GeometryPlan | null) => void;
    onValidation: (validationResponse: ValidationResponse) => void;
    setLoading: (loading: boolean) => void;
} & InfraModelBaseProps;

export const InfraModelEditLoader: React.FC<InfraModelLoaderProps> = ({ ...props }) => {
    const { id: planId } = useParams<{ id: string }>();
    const [isLoading, setIsLoading] = useState(true);
    const [initializedPlanId, setInitializedPlanId] = useState<GeometryPlanId | null>(null);

    const extraParams = props.extraInfraModelParameters;
    const overrideParams = props.overrideInfraModelParameters;

    const onValidate: () => void = async () => {
        if (planId && planId === initializedPlanId) {
            props.setLoading(true);
            props.onValidation(await getValidationErrorsForGeometryPlan(planId, overrideParams));
            props.setLoading(false);
        }
    };
    // Automatically re-validate whenever the manually input data changes
    React.useEffect(() => {
        onValidate();
    }, [overrideParams, initializedPlanId]);

    const onSave: () => Promise<boolean> = async () => {
        if (planId && planId === initializedPlanId) {
            props.setLoading(true);
            const response = await updateGeometryPlan(planId, extraParams, overrideParams);
            props.setLoading(false);
            return response != null;
        } else return false;
    };

    useEffect(() => {
        if (planId) {
            setIsLoading(true);
            getGeometryPlan(planId).then((plan: GeometryPlan | null) => {
                props.setExistingInfraModel(plan);
                setIsLoading(false);
                setInitializedPlanId(planId);
            });
        }
    }, [planId]);

    return isLoading ? (
        <Spinner />
    ) : (
        <InfraModelView {...props} onSave={onSave} onValidate={onValidate} />
    );
};
