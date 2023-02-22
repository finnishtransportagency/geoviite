import React from 'react';
import { GeometryPlanId } from 'geometry/geometry-model';
import { NavLink } from 'react-router-dom';
import { Link } from 'vayla-design-lib/link/link';

export type GeometryPlanNameLinkProps = {
    planId: GeometryPlanId;
    planName: string;
};

export const GeometryPlanNameLink: React.FC<GeometryPlanNameLinkProps> = (
    props: GeometryPlanNameLinkProps,
) => {
    return (
        <NavLink to={`/infra-model/edit/${props.planId}`}>
            <Link>{props.planName}</Link>
        </NavLink>
    );
};
