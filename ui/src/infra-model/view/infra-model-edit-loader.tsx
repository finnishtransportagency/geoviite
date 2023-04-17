import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getGeometryPlan } from 'geometry/geometry-api';
import { GeometryPlan } from 'geometry/geometry-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { InfraModelView, InfraModelViewProps } from 'infra-model/view/infra-model-view';
import { GeometryPlanWithParameters } from 'infra-model/infra-model-slice';

export type InfraModelLoaderProps = {
    setExistingInfraModel: (plan: GeometryPlanWithParameters) => void;
} & InfraModelViewProps;

export const InfraModelEditLoader: React.FC<InfraModelLoaderProps> = ({
    setExistingInfraModel,
    ...props
}) => {
    const params = useParams<{ id: string }>();
    const [isLoading, setIsLoading] = useState(true);
    useEffect(() => {
        if (params.id !== undefined) {
            setIsLoading(true);
            getGeometryPlan(params.id).then((plan: GeometryPlan | null) => {
                if (plan) {
                    setExistingInfraModel({
                        geometryPlan: plan,
                        extraInfraModelParameters: {
                            oid: plan.oid ? plan.oid : undefined,
                            planPhase: plan.planPhase ? plan.planPhase : undefined,
                            decisionPhase: plan.decisionPhase ? plan.decisionPhase : undefined,
                            measurementMethod: plan.measurementMethod
                                ? plan.measurementMethod
                                : undefined,
                            message: plan.message ? plan.message : undefined,
                        },
                    });
                    setIsLoading(false);
                }
            });
        }
    }, [params.id]);

    return isLoading ? <Spinner /> : <InfraModelView {...props} />;
};
