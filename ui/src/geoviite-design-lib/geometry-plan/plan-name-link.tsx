import React from 'react';
import { GeometryPlanId } from 'geometry/geometry-model';
import styles from 'vayla-design-lib/link/link.scss';
import { NavLink } from 'react-router-dom';

export type GeometryPlanNameLinkProps = {
    planId: GeometryPlanId;
    planName: string;
};

export const PlanNameLink: React.FC<GeometryPlanNameLinkProps> = (
    props: GeometryPlanNameLinkProps,
) => {
    return (
        <NavLink className={styles.link} to={`/infra-model/edit/${props.planId}`}>
            {props.planName}
        </NavLink>
    );
};
