import { usePlanHeader } from 'track-layout/track-layout-react-utils';
import { Link } from 'vayla-design-lib/link/link';
import React from 'react';
import { useTrackLayoutAppDispatch } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
import { createEmptyItemCollections } from 'selection/selection-store';
import { GeometryPlanId } from 'geometry/geometry-model';

export type GeometryPlanLinkProps = {
    planId?: GeometryPlanId;
    onClick?: (planId: GeometryPlanId) => void;
};

function createSelectAction() {
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);
    return (planId: GeometryPlanId) =>
        delegates.onSelect({
            ...createEmptyItemCollections(),
            geometryPlans: [planId],
        });
}

export const GeometryPlanLink: React.FC<GeometryPlanLinkProps> = (props: GeometryPlanLinkProps) => {
    const header = usePlanHeader(props.planId);
    const clickAction = props.onClick || createSelectAction();
    return <Link onClick={() => header && clickAction(header.id)}>{header?.fileName || ''}</Link>;
};
