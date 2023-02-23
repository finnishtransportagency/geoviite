import React from 'react';
import { GeometryPlanId } from 'geometry/geometry-model';
import { Link } from 'vayla-design-lib/link/link';
import { useAppNavigate } from 'common/navigate';

export type GeometryPlanNameLinkProps = {
    planId: GeometryPlanId;
    planName: string;
};

export const GeometryPlanNameLink: React.FC<GeometryPlanNameLinkProps> = (
    props: GeometryPlanNameLinkProps,
) => {
    const navigate = useAppNavigate();
    return <Link onClick={() => navigate('inframodel-edit', props.planId)}>{props.planName}</Link>;
};
