import React from 'react';
import { GeometryPlanId } from 'geometry/geometry-model';
import { NavLink } from 'react-router-dom';

export type GeometryPlanNameLinkProps = {
    planId: GeometryPlanId;
    planName: string;
};

export const GeometryPlanNameLink: React.FC<GeometryPlanNameLinkProps> = (
    props: GeometryPlanNameLinkProps,
) => {
    return <NavLink to={`/infra-model/edit/${props.planId}`}>{props.planName}</NavLink>;
};
