import { usePlanHeader } from 'track-layout/track-layout-react-utils';
import { Link } from 'vayla-design-lib/link/link';
import React from 'react';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createEmptyItemCollections } from 'selection/selection-store';
import { GeometryPlanId } from 'geometry/geometry-model';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

export type GeometryPlanLinkProps = {
    planId?: GeometryPlanId;
    onClick?: (planId: GeometryPlanId) => void;
};

function createSelectAction() {
    const delegates = createDelegates(TrackLayoutActions);
    return (planId: GeometryPlanId) =>
        delegates.onSelect({
            ...createEmptyItemCollections(),
            geometryPlans: [planId],
        });
}

export const GeometryPlanLink: React.FC<GeometryPlanLinkProps> = (props: GeometryPlanLinkProps) => {
    const header = usePlanHeader(props.planId);
    const clickAction = props.onClick || createSelectAction();
    return (
        <AnchorLink onClick={() => header && clickAction(header.id)}>
            {header?.fileName || ''}
        </AnchorLink>
    );
};
