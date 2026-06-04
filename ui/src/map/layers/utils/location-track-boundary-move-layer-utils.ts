import { AlignmentDataHolder } from 'track-layout/layout-map-api';
import {
    AlignmentPoint,
    AlignmentStartAndEnd,
    LayoutSwitch,
    LocationTrackSwitchJoint,
    SwitchJointId,
} from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';
import { expectDefined } from 'utils/type-utils';
import {
    BoundaryOrientation,
    SelectedBoundaryMoveJoint,
} from 'track-layout/track-boundary-move-api';

export type BoundaryMoveTrackRole = 'head' | 'counterpart';

export type BoundaryMoveTrackInfo = {
    role: BoundaryMoveTrackRole;
    alignment: AlignmentDataHolder;
    joints: LocationTrackSwitchJoint[];
    switches: LayoutSwitch[];
    startAndEnd: AlignmentStartAndEnd | undefined;
};

export type BoundaryMoveTrackInfos = {
    headTrack: BoundaryMoveTrackInfo;
    counterpartTrack: BoundaryMoveTrackInfo | undefined;
};

export type MoveInterval = {
    fromTrack: BoundaryMoveTrackRole;
    movingMRangeStart: number;
    movingMRangeEnd: number;
};

export type BoundaryBar = {
    // The point the bar is centred on.
    center: Point;
    // Unit vector along which the bar is drawn, i.e. perpendicular to the track.
    direction: Point;
};

function unitVector(v: Point): Point | undefined {
    const length = Math.hypot(v.x, v.y);
    return length === 0 ? undefined : { x: v.x / length, y: v.y / length };
}

function perpendicular(v: Point): Point {
    return { x: -v.y, y: v.x };
}

function second<T>(points: T[]): T | undefined {
    return points.length >= 2 ? points[1] : undefined;
}

function secondToLast<T>(points: T[]): T | undefined {
    return points.length >= 2 ? points[points.length - 2] : undefined;
}

export function isBoundaryJoint(
    joint: LocationTrackSwitchJoint,
    boundaryJointId: SwitchJointId | undefined,
): boolean {
    return (
        boundaryJointId !== undefined &&
        joint.switchId === boundaryJointId.switchId &&
        joint.jointNumber === boundaryJointId.jointNumber
    );
}

type JointOnTrack = {
    track: BoundaryMoveTrackInfo;
    joint: LocationTrackSwitchJoint;
};

function findJointOnTracks(
    trackInfos: BoundaryMoveTrackInfos,
    selectedJoint: SelectedBoundaryMoveJoint,
): JointOnTrack | undefined {
    const track =
        selectedJoint.role === 'head' ? trackInfos.headTrack : trackInfos.counterpartTrack;
    if (track === undefined) {
        return undefined;
    }
    const joint = track.joints.find(
        (j) =>
            j.switchId === selectedJoint.joint.switchId &&
            j.jointNumber === selectedJoint.joint.jointNumber,
    );
    return joint === undefined ? undefined : { track, joint };
}

export function findIntervalToMove(
    trackInfos: BoundaryMoveTrackInfos,
    orientation: BoundaryOrientation,
    selectedJoint: SelectedBoundaryMoveJoint,
): MoveInterval | undefined {
    const found = findJointOnTracks(trackInfos, selectedJoint);
    if (found === undefined) {
        return undefined;
    }

    const headFirst = orientation === 'HEAD_FIRST';
    const movingTowardsEnd = headFirst === (found.track.role === 'head');
    const jointTrackEndM = expectDefined(found.track.startAndEnd?.end?.point?.m);
    return {
        fromTrack: found.track.role,
        movingMRangeStart: movingTowardsEnd ? found.joint.m : 0,
        movingMRangeEnd: movingTowardsEnd ? jointTrackEndM : found.joint.m,
    };
}

// Unit tangent of the alignment at the given m-value, or undefined if m falls outside
// the alignment or the surrounding segment has zero length.
export function unitDirectionAtM(points: AlignmentPoint[], m: number): Point | undefined {
    for (let i = 0; i < points.length - 1; i++) {
        const a = expectDefined(points[i]);
        const b = expectDefined(points[i + 1]);
        if (a.m <= m && m <= b.m) {
            const direction = unitVector({ x: b.x - a.x, y: b.y - a.y });
            if (direction !== undefined) {
                return direction;
            }
        }
    }
    return undefined;
}

function boundaryBarAtJoint(
    trackInfos: BoundaryMoveTrackInfos,
    selectedJoint: SelectedBoundaryMoveJoint,
): BoundaryBar | undefined {
    const found = findJointOnTracks(trackInfos, selectedJoint);
    if (found === undefined) {
        return undefined;
    }
    const tangent = unitDirectionAtM(found.track.alignment.points, found.joint.m);
    if (tangent === undefined) {
        return undefined;
    }
    return { center: found.joint.location, direction: perpendicular(tangent) };
}

function boundaryBarAtOriginalBoundary(
    trackInfos: BoundaryMoveTrackInfos,
    orientation: BoundaryOrientation,
): BoundaryBar | undefined {
    const headEnds = trackInfos.headTrack.startAndEnd;
    if (!headEnds?.start || !headEnds.end) {
        return undefined;
    }

    const headPoints = trackInfos.headTrack.alignment.points;
    const counterpartPoints = trackInfos.counterpartTrack?.alignment.points ?? [];

    const headFirst = orientation === 'HEAD_FIRST';
    const boundaryPoint = headFirst ? headEnds.end.point : headEnds.start.point;
    // track endpoints might be same, but the bar is only visual, so make a guess that the
    // second-to-first/last point is fine and good enough
    const before = headFirst ? secondToLast(headPoints) : secondToLast(counterpartPoints);
    const after = headFirst ? second(counterpartPoints) : second(headPoints);
    if (before === undefined || after === undefined) {
        return undefined;
    }

    const tangent = unitVector({ x: after.x - before.x, y: after.y - before.y });
    if (tangent === undefined) {
        return undefined;
    }
    return {
        center: { x: boundaryPoint.x, y: boundaryPoint.y },
        direction: perpendicular(tangent),
    };
}

export function computeBoundaryBar(
    trackInfos: BoundaryMoveTrackInfos,
    orientation: BoundaryOrientation | undefined,
    selectedJoint: SelectedBoundaryMoveJoint | undefined,
): BoundaryBar | undefined {
    if (selectedJoint !== undefined) {
        return boundaryBarAtJoint(trackInfos, selectedJoint);
    }
    return orientation === undefined
        ? undefined
        : boundaryBarAtOriginalBoundary(trackInfos, orientation);
}

export type BoundaryMoveTrackColor = BoundaryMoveTrackRole;

export function boundaryMoveJointColor(
    trackRole: BoundaryMoveTrackRole,
    joint: LocationTrackSwitchJoint,
    boundaryJointId: SwitchJointId | undefined,
    moveInterval: MoveInterval | undefined,
): BoundaryMoveTrackColor {
    if (isBoundaryJoint(joint, boundaryJointId)) {
        return 'head';
    }
    const inMoveInterval =
        moveInterval !== undefined &&
        trackRole === moveInterval.fromTrack &&
        joint.m >= moveInterval.movingMRangeStart &&
        joint.m <= moveInterval.movingMRangeEnd;
    return inMoveInterval ? (trackRole === 'head' ? 'counterpart' : 'head') : trackRole;
}

// Visual reassignment of the points from one alignment to the other
export function moveMoveInterval(
    orientation: BoundaryOrientation,
    moveInterval: MoveInterval,
    headPoints: AlignmentPoint[],
    counterpartPoints: AlignmentPoint[],
): { renderedHead: AlignmentPoint[]; renderedCounterpart: AlignmentPoint[] } {
    const { fromTrack, movingMRangeStart, movingMRangeEnd } = moveInterval;
    const headFirst = orientation === 'HEAD_FIRST';
    const fromHead = fromTrack === 'head';
    const sourcePoints = fromHead ? headPoints : counterpartPoints;
    const staying = sourcePoints.filter((p) => p.m < movingMRangeStart || p.m > movingMRangeEnd);
    const moving = sourcePoints.filter((p) => p.m >= movingMRangeStart && p.m <= movingMRangeEnd);
    return fromHead
        ? {
              renderedHead: staying,
              renderedCounterpart: headFirst
                  ? [...moving, ...counterpartPoints]
                  : [...counterpartPoints, ...moving],
          }
        : {
              renderedHead: headFirst ? [...headPoints, ...moving] : [...moving, ...headPoints],
              renderedCounterpart: staying,
          };
}
