import React from 'react';
import { GeometryPlanId } from 'geometry/geometry-model';
import styles from 'vayla-design-lib/link/link.scss';

export type GeometryPlanNameLinkProps = {
    planId: GeometryPlanId;
    planName: string;
};

export const PlanNameLink: React.FC<GeometryPlanNameLinkProps> = (
    props: GeometryPlanNameLinkProps,
) => {
    return (
        <a className={styles.link} href={`#/infra-model/edit/${props.planId}`}>
            {props.planName}
        </a>
    );
};
