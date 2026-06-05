import { LayoutSwitchId, LocationTrackId } from 'track-layout/track-layout-model';
import { JointNumber, LayoutContext } from 'common/common-model';
import { Point } from 'model/geometry';
import { getNonNull, postNonNull } from 'api/api-fetch';
import { TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';
import { BoundaryMoveTrackRole } from 'map/layers/utils/location-track-boundary-move-layer-utils';

export type BoundaryOrientation = 'HEAD_FIRST' | 'COUNTERPART_FIRST';

type SwitchJointId = {
    switchId: LayoutSwitchId;
    jointNumber: JointNumber;
};

// The user picks where the boundary between the two tracks should end up: either at a switch joint
// somewhere along one of the tracks, or all the way at the far end of one of the tracks (which leaves
// that track empty).
export type SelectedBoundaryMoveJoint = {
    kind: 'joint';
    role: BoundaryMoveTrackRole;
    joint: SwitchJointId;
    location: Point;
};

export type SelectedBoundaryMoveEnd = {
    kind: 'end';
    role: BoundaryMoveTrackRole;
    location: Point;
};

export type SelectedBoundaryMoveTarget = SelectedBoundaryMoveJoint | SelectedBoundaryMoveEnd;

export type BoundaryMoveCounterpart = {
    trackId: LocationTrackId;
    orientation: BoundaryOrientation;
    connectingSwitchJoint: SwitchJointId | undefined;
};

export type BoundaryMoveDirection = 'ASCENDING' | 'DESCENDING';

export async function getTrackBoundaryMoveCounterpartOptions(
    layoutContext: LayoutContext,
    locationTrackId: LocationTrackId,
): Promise<BoundaryMoveCounterpart[]> {
    return getNonNull<BoundaryMoveCounterpart[]>(
        `${TRACK_LAYOUT_URI}/track-boundary-move/${layoutContext.branch}/counterpart-options/${locationTrackId}`,
    );
}

export type TrackBoundaryMoveId = string;

export type TrackBoundaryMoveRequest = {
    shorteningTrackId: LocationTrackId;
    lengtheningTrackId: LocationTrackId;
    upToSwitchJoint: SwitchJointId | undefined;
    boundaryMoveDirection: BoundaryMoveDirection;
};

export async function saveTrackBoundaryMove(
    layoutContext: LayoutContext,
    request: TrackBoundaryMoveRequest,
): Promise<TrackBoundaryMoveId> {
    return postNonNull<TrackBoundaryMoveRequest, TrackBoundaryMoveId>(
        `${TRACK_LAYOUT_URI}/track-boundary-move/${layoutContext.branch}/`,
        request,
    );
}
