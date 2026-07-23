import { AlignmentDataHolder } from 'track-layout/layout-map-api';
import {
    AlignmentPoint,
    AlignmentStartAndEnd,
    LayoutLocationTrack,
    LayoutSwitch,
    LocationTrackSwitchJoint,
    SwitchJointId,
} from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';
import { expectDefined } from 'utils/type-utils';
import {
    BoundaryOrientation,
    SelectedBoundaryMoveEnd,
    SelectedBoundaryMoveJoint,
    SelectedBoundaryMoveTarget,
} from 'track-layout/track-boundary-move-api';

export type BoundaryMoveTrackRole = 'head' | 'counterpart';

export type BoundaryMoveTrackInfo = {
    role: BoundaryMoveTrackRole;
    locationTrack: LayoutLocationTrack;
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

// The far end of a track (the end away from the boundary) is its start when the track comes first along
// the combined geometry, otherwise its end.
export function farEndIsStart(
    role: BoundaryMoveTrackRole,
    orientation: BoundaryOrientation,
): boolean {
    const headFirst = orientation === 'HEAD_FIRST';
    return (role === 'head') === headFirst;
}

function trackByRole(
    trackInfos: BoundaryMoveTrackInfos,
    role: BoundaryMoveTrackRole,
): BoundaryMoveTrackInfo | undefined {
    return role === 'head' ? trackInfos.headTrack : trackInfos.counterpartTrack;
}

export type SelectableTrackEnd = {
    role: BoundaryMoveTrackRole;
    location: Point;
};

// The far end (the end away from the boundary) of a track, or undefined if unknown.
function farEndPoint(
    track: BoundaryMoveTrackInfo,
    orientation: BoundaryOrientation,
): AlignmentPoint | undefined {
    const isStart = farEndIsStart(track.role, orientation);
    return (isStart ? track.startAndEnd?.start : track.startAndEnd?.end)?.point;
}

export function selectableTrackEnd(
    track: BoundaryMoveTrackInfo,
    orientation: BoundaryOrientation,
): SelectableTrackEnd | undefined {
    const point = farEndPoint(track, orientation);
    return point === undefined
        ? undefined
        : { role: track.role, location: { x: point.x, y: point.y } };
}

// Tolerance (m) for treating a joint's m-value as coinciding with the track's far end.
const FAR_END_JOINT_M_TOLERANCE = 0.001;

export function isFarEndJoint(
    joint: LocationTrackSwitchJoint,
    track: BoundaryMoveTrackInfo,
    orientation: BoundaryOrientation,
): boolean {
    const endM = farEndPoint(track, orientation)?.m;
    return endM !== undefined && Math.abs(joint.m - endM) <= FAR_END_JOINT_M_TOLERANCE;
}

function endMoveInterval(
    trackInfos: BoundaryMoveTrackInfos,
    target: SelectedBoundaryMoveEnd,
): MoveInterval | undefined {
    const track = trackByRole(trackInfos, target.role);
    const trackEndM = track?.startAndEnd?.end?.point?.m;
    if (track === undefined || trackEndM === undefined) {
        return undefined;
    }
    // The whole track moves over.
    return { fromTrack: target.role, movingMRangeStart: 0, movingMRangeEnd: trackEndM };
}

export function findIntervalToMove(
    trackInfos: BoundaryMoveTrackInfos,
    orientation: BoundaryOrientation,
    target: SelectedBoundaryMoveTarget,
): MoveInterval | undefined {
    if (target.kind === 'end') {
        return endMoveInterval(trackInfos, target);
    }
    const found = findJointOnTracks(trackInfos, target);
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

function boundaryBarAtEnd(
    trackInfos: BoundaryMoveTrackInfos,
    orientation: BoundaryOrientation,
    target: SelectedBoundaryMoveEnd,
): BoundaryBar | undefined {
    const track = trackByRole(trackInfos, target.role);
    if (track === undefined) {
        return undefined;
    }
    const isStart = farEndIsStart(target.role, orientation);
    const point = (isStart ? track.startAndEnd?.start : track.startAndEnd?.end)?.point;
    if (point === undefined) {
        return undefined;
    }
    const tangent = unitDirectionAtM(track.alignment.points, point.m);
    if (tangent === undefined) {
        return undefined;
    }
    return { center: { x: point.x, y: point.y }, direction: perpendicular(tangent) };
}

export function computeBoundaryBar(
    trackInfos: BoundaryMoveTrackInfos,
    orientation: BoundaryOrientation | undefined,
    target: SelectedBoundaryMoveTarget | undefined,
): BoundaryBar | undefined {
    if (target?.kind === 'joint') {
        return boundaryBarAtJoint(trackInfos, target);
    }
    if (orientation === undefined) {
        return undefined;
    }
    return target?.kind === 'end'
        ? boundaryBarAtEnd(trackInfos, orientation, target)
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
