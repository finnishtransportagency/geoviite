import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getGeometryPlan } from 'geometry/geometry-api';
import { GeometryPlan } from 'geometry/geometry-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { InfraModelBaseProps, InfraModelView } from 'infra-model/view/infra-model-view';
import { ValidationResponse } from 'infra-model/infra-model-slice';
import {
    getValidationErrorsForGeometryPlan,
    updateGeometryPlan,
} from 'infra-model/infra-model-api';

export type InfraModelLoaderProps = {
    setExistingInfraModel: (plan: GeometryPlan) => void;
    onValidation: (validationResponse: ValidationResponse) => void;
    setLoading: (loading: boolean) => void;
} & InfraModelBaseProps;

export const InfraModelEditLoader: React.FC<InfraModelLoaderProps> = ({
    setExistingInfraModel,
    ...props
}) => {
    const { id: planId } = useParams<{ id: string }>();
    const [isLoading, setIsLoading] = useState(true);

    const extraParams = props.extraInfraModelParameters;
    const overrideParams = props.overrideInfraModelParameters;

    const onValidate: () => Promise<null> = async () => {
        if (planId) {
            props.setLoading(true);
            props.onValidation(await getValidationErrorsForGeometryPlan(planId, overrideParams));
            props.setLoading(false);
        }
        return null;
    };
    // Automatically re-validate whenever the manually input data changes
    React.useEffect(() => {
        onValidate();
    }, [overrideParams]);

    const onSave: () => Promise<boolean> = async () => {
        if (!planId) return false;
        props.setLoading(true);
        const response = await updateGeometryPlan(planId, extraParams, overrideParams);
        props.setLoading(false);
        return response != null;
    };

    useEffect(() => {
        if (planId !== undefined) {
            setIsLoading(true);
            getGeometryPlan(planId).then((plan: GeometryPlan | null) => {
                if (plan) setExistingInfraModel(plan);
                setIsLoading(false);
            });
            onValidate();
        }
    }, [planId]);
    useEffect;

    return isLoading ? (
        <Spinner />
    ) : (
        <InfraModelView {...props} onSave={onSave} onValidate={onValidate} />
    );
};
