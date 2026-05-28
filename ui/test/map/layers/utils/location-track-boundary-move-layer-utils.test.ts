import { describe, expect, test } from '@jest/globals';
import {
    boundaryMoveJointColor,
    BoundaryMoveTrackInfo,
    BoundaryMoveTrackInfos,
    BoundaryMoveTrackRole,
    computeBoundaryBar,
    findIntervalToMove,
    MoveInterval,
    moveMoveInterval,
    unitDirectionAtM,
} from 'map/layers/utils/location-track-boundary-move-layer-utils';
import {
    AlignmentPoint,
    AlignmentStartAndEnd,
    LayoutSwitchId,
    LocationTrackSwitchJoint,
    SwitchJointId,
} from 'track-layout/track-layout-model';
import { AlignmentDataHolder } from 'track-layout/layout-map-api';
import { JointNumber } from 'common/common-model';
import { SelectedBoundaryMoveJoint } from 'track-layout/track-boundary-move-api';

const switchId = (id: string) => id as LayoutSwitchId;
const jointNumber = (n: string) => n as JointNumber;

const point = (x: number, y: number, m: number): AlignmentPoint => ({ x, y, m });

const startAndEnd = (
    start: AlignmentPoint | undefined,
    end: AlignmentPoint | undefined,
): AlignmentStartAndEnd =>
    ({
        id: 'test',
        start: start === undefined ? undefined : { point: start },
        end: end === undefined ? undefined : { point: end },
    }) as AlignmentStartAndEnd;

const joint = (
    sId: string,
    jNumber: string,
    x: number,
    y: number,
    m: number,
): LocationTrackSwitchJoint => ({
    switchId: switchId(sId),
    jointNumber: jointNumber(jNumber),
    location: { x, y },
    m,
});

const jointId = (sId: string, jNumber: string): SwitchJointId => ({
    switchId: switchId(sId),
    jointNumber: jointNumber(jNumber),
});

const selectedJoint = (
    role: BoundaryMoveTrackRole,
    sId: string,
    jNumber: string,
): SelectedBoundaryMoveJoint => ({
    role,
    joint: jointId(sId, jNumber),
});

const trackInfo = (opts: {
    points?: AlignmentPoint[];
    joints?: LocationTrackSwitchJoint[];
    startAndEnd?: AlignmentStartAndEnd;
}): BoundaryMoveTrackInfo => ({
    role: 'head',
    alignment: { points: opts.points ?? [] } as AlignmentDataHolder,
    joints: opts.joints ?? [],
    switches: [],
    startAndEnd: opts.startAndEnd,
});

const trackInfos = (
    headTrack: BoundaryMoveTrackInfo,
    counterpartTrack?: BoundaryMoveTrackInfo,
): BoundaryMoveTrackInfos => ({
    headTrack: { ...headTrack, role: 'head' },
    counterpartTrack:
        counterpartTrack === undefined ? undefined : { ...counterpartTrack, role: 'counterpart' },
});

// A near-zero comparison for the unit vectors we get out of the geometry helpers.
const closeTo = (actual: number, expected: number) => expect(actual).toBeCloseTo(expected, 10);

describe('unitDirectionAtM', () => {
    const points = [point(0, 0, 0), point(10, 0, 10), point(10, 10, 20)];

    test('returns the unit tangent within the first segment', () => {
        const dir = unitDirectionAtM(points, 5);
        expect(dir).toBeDefined();
        closeTo(dir!.x, 1);
        closeTo(dir!.y, 0);
    });

    test('returns the unit tangent within the second segment', () => {
        const dir = unitDirectionAtM(points, 15);
        expect(dir).toBeDefined();
        closeTo(dir!.x, 0);
        closeTo(dir!.y, 1);
    });

    test('returns undefined for an m-value outside the alignment', () => {
        expect(unitDirectionAtM(points, 100)).toBeUndefined();
    });

    test('skips zero-length segments and uses the next real one', () => {
        const withDuplicate = [point(0, 0, 0), point(0, 0, 5), point(3, 4, 15)];
        const dir = unitDirectionAtM(withDuplicate, 5);
        expect(dir).toBeDefined();
        closeTo(dir!.x, 0.6);
        closeTo(dir!.y, 0.8);
    });
});

describe('findIntervalToMove', () => {
    // Head runs 0..100 along +x, counterpart continues 100..200; they meet head-first.
    const headEnds = startAndEnd(point(0, 0, 0), point(100, 0, 100));
    const counterpartEnds = startAndEnd(point(100, 0, 0), point(200, 0, 100));

    const tracks = (jointOnHead: boolean, jointM: number): BoundaryMoveTrackInfos => {
        const j = joint('sw1', '1', 0, 0, jointM);
        return trackInfos(
            trackInfo({
                joints: jointOnHead ? [j] : [],
                startAndEnd: headEnds,
            }),
            trackInfo({
                joints: jointOnHead ? [] : [j],
                startAndEnd: counterpartEnds,
            }),
        );
    };

    test('joint on head track (head-first) moves towards the end', () => {
        expect(
            findIntervalToMove(tracks(true, 40), 'HEAD_FIRST', selectedJoint('head', 'sw1', '1')),
        ).toEqual({
            fromTrack: 'head',
            movingMRangeStart: 40,
            movingMRangeEnd: 100,
        });
    });

    test('joint on counterpart track (head-first) moves towards the start', () => {
        expect(
            findIntervalToMove(
                tracks(false, 40),
                'HEAD_FIRST',
                selectedJoint('counterpart', 'sw1', '1'),
            ),
        ).toEqual({
            fromTrack: 'counterpart',
            movingMRangeStart: 0,
            movingMRangeEnd: 40,
        });
    });

    test('undefined when the joint is on neither track', () => {
        expect(
            findIntervalToMove(tracks(true, 40), 'HEAD_FIRST', selectedJoint('head', 'sw9', '9')),
        ).toBeUndefined();
    });
});

describe('boundaryMoveJointColor', () => {
    const moveInterval: MoveInterval = {
        fromTrack: 'head',
        movingMRangeStart: 40,
        movingMRangeEnd: 100,
    };
    const boundaryJointId = jointId('sw-boundary', '1');

    test('boundary joint is always head colour', () => {
        const j = joint('sw-boundary', '1', 0, 0, 50);
        // even though it sits on the counterpart track and inside no move interval
        expect(boundaryMoveJointColor('counterpart', j, boundaryJointId, undefined)).toBe('head');
    });

    test('head joint outside the move interval keeps head colour', () => {
        const j = joint('sw1', '1', 0, 0, 10);
        expect(boundaryMoveJointColor('head', j, boundaryJointId, moveInterval)).toBe('head');
    });

    test('head joint inside the move interval takes counterpart colour', () => {
        const j = joint('sw1', '1', 0, 0, 50);
        expect(boundaryMoveJointColor('head', j, boundaryJointId, moveInterval)).toBe(
            'counterpart',
        );
    });

    test('counterpart joint outside any move interval keeps counterpart colour', () => {
        const j = joint('sw2', '1', 0, 0, 10);
        expect(boundaryMoveJointColor('counterpart', j, boundaryJointId, undefined)).toBe(
            'counterpart',
        );
    });

    test('counterpart joint inside its move interval takes head colour', () => {
        const j = joint('sw2', '1', 0, 0, 50);
        const counterpartInterval: MoveInterval = {
            fromTrack: 'counterpart',
            movingMRangeStart: 40,
            movingMRangeEnd: 100,
        };
        expect(boundaryMoveJointColor('counterpart', j, boundaryJointId, counterpartInterval)).toBe(
            'head',
        );
    });
});

describe('moveMoveInterval', () => {
    const headPoints = [point(0, 0, 0), point(10, 0, 10), point(20, 0, 20)];
    const counterpartPoints = [point(20, 0, 0), point(30, 0, 10)];

    test('moves a head-track interval onto a head-first counterpart', () => {
        const moveInterval: MoveInterval = {
            fromTrack: 'head',
            movingMRangeStart: 10,
            movingMRangeEnd: 20,
        };
        const { renderedHead, renderedCounterpart } = moveMoveInterval(
            'HEAD_FIRST',
            moveInterval,
            headPoints,
            counterpartPoints,
        );
        expect(renderedHead.map((p) => p.m)).toEqual([0]);
        // moved points (m 10, 20) prepended to the counterpart points
        expect(renderedCounterpart).toEqual([
            point(10, 0, 10),
            point(20, 0, 20),
            ...counterpartPoints,
        ]);
    });

    test('counterpart-first counterpart appends moved points after the counterpart', () => {
        const moveInterval: MoveInterval = {
            fromTrack: 'head',
            movingMRangeStart: 10,
            movingMRangeEnd: 20,
        };
        const { renderedCounterpart } = moveMoveInterval(
            'COUNTERPART_FIRST',
            moveInterval,
            headPoints,
            counterpartPoints,
        );
        expect(renderedCounterpart).toEqual([
            ...counterpartPoints,
            point(10, 0, 10),
            point(20, 0, 20),
        ]);
    });

    test('moves a counterpart-track interval onto the head', () => {
        const moveInterval: MoveInterval = {
            fromTrack: 'counterpart',
            movingMRangeStart: 0,
            movingMRangeEnd: 0,
        };
        const { renderedHead, renderedCounterpart } = moveMoveInterval(
            'HEAD_FIRST',
            moveInterval,
            headPoints,
            counterpartPoints,
        );
        expect(renderedCounterpart.map((p) => p.m)).toEqual([10]);
        expect(renderedHead).toEqual([...headPoints, point(20, 0, 0)]);
    });
});

describe('computeBoundaryBar', () => {
    describe('at a selected joint', () => {
        const tracks = trackInfos(
            trackInfo({
                points: [point(0, 0, 0), point(10, 0, 10), point(20, 0, 20)],
                joints: [joint('sw1', '1', 10, 0, 10)],
            }),
        );

        test('is centred on the joint and perpendicular to the track', () => {
            const bar = computeBoundaryBar(tracks, undefined, selectedJoint('head', 'sw1', '1'));
            expect(bar).toBeDefined();
            expect(bar!.center).toEqual({ x: 10, y: 0 });
            // track runs along +x, so the bar points along ±y
            closeTo(bar!.direction.x, 0);
            closeTo(Math.abs(bar!.direction.y), 1);
        });

        test('is a unit direction', () => {
            const bar = computeBoundaryBar(tracks, undefined, selectedJoint('head', 'sw1', '1'))!;
            closeTo(Math.hypot(bar.direction.x, bar.direction.y), 1);
        });

        test('undefined when the selected joint is not found', () => {
            expect(
                computeBoundaryBar(tracks, undefined, selectedJoint('head', 'nope', '1')),
            ).toBeUndefined();
        });
    });

    describe('at the original boundary', () => {
        // Head ends at (10, 0) coming from the west; counterpart starts at (10, 0)
        // continuing to the north-east. They share the exact same point, no joint.
        const head = trackInfo({
            points: [point(0, 0, 0), point(5, 0, 5), point(10, 0, 10)],
            startAndEnd: startAndEnd(point(0, 0, 0), point(10, 0, 10)),
        });
        const counterpart = trackInfo({
            points: [point(10, 0, 0), point(13, 4, 5)],
            startAndEnd: startAndEnd(point(10, 0, 0), point(13, 4, 5)),
        });

        test('is centred on the shared boundary point', () => {
            const bar = computeBoundaryBar(trackInfos(head, counterpart), 'HEAD_FIRST', undefined);
            expect(bar).toBeDefined();
            expect(bar!.center).toEqual({ x: 10, y: 0 });
        });

        test('derives a non-degenerate direction from the surrounding points', () => {
            // tangent runs from head's second-to-last (5,0) to counterpart's second
            // point (13,4): (8,4) normalised; bar is perpendicular to that.
            const bar = computeBoundaryBar(trackInfos(head, counterpart), 'HEAD_FIRST', undefined)!;
            const tangent = { x: 8, y: 4 };
            const tLen = Math.hypot(tangent.x, tangent.y);
            // perpendicular means the dot product with the tangent is zero
            const dot = bar.direction.x * (tangent.x / tLen) + bar.direction.y * (tangent.y / tLen);
            closeTo(dot, 0);
            closeTo(Math.hypot(bar.direction.x, bar.direction.y), 1);
        });

        test('counterpart-first orientation centres on the head start', () => {
            const headCp = trackInfo({
                points: [point(10, 0, 0), point(15, 0, 5)],
                startAndEnd: startAndEnd(point(10, 0, 0), point(15, 0, 5)),
            });
            const counterpartCp = trackInfo({
                points: [point(0, 0, 0), point(5, 0, 5), point(10, 0, 10)],
                startAndEnd: startAndEnd(point(0, 0, 0), point(10, 0, 10)),
            });
            const bar = computeBoundaryBar(
                trackInfos(headCp, counterpartCp),
                'COUNTERPART_FIRST',
                undefined,
            );
            expect(bar).toBeDefined();
            expect(bar!.center).toEqual({ x: 10, y: 0 });
        });

        test('undefined when a track is too short to derive a direction', () => {
            const shortCounterpart = trackInfo({
                points: [point(10, 0, 0)],
                startAndEnd: startAndEnd(point(10, 0, 0), point(10, 0, 0)),
            });
            expect(
                computeBoundaryBar(trackInfos(head, shortCounterpart), 'HEAD_FIRST', undefined),
            ).toBeUndefined();
        });

        test('undefined when there is no counterpart track at all', () => {
            expect(computeBoundaryBar(trackInfos(head), 'HEAD_FIRST', undefined)).toBeUndefined();
        });

        // Degenerate case the counterpart selection can let through: identical geometry.
        // The boundary point and the second points coincide, so no direction; must not throw.
        test('does not throw on identical reversed geometry, returns undefined', () => {
            const a = trackInfo({
                points: [point(0, 0, 0), point(10, 0, 10)],
                startAndEnd: startAndEnd(point(0, 0, 0), point(10, 0, 10)),
            });
            const b = trackInfo({
                points: [point(10, 0, 0), point(0, 0, 10)],
                startAndEnd: startAndEnd(point(10, 0, 0), point(0, 0, 10)),
            });
            // head end (10,0) meets counterpart start (10,0): HEAD_FIRST.
            // tangent from head's (0,0) to counterpart's (0,0) is zero-length.
            expect(() =>
                computeBoundaryBar(trackInfos(a, b), 'HEAD_FIRST', undefined),
            ).not.toThrow();
            expect(computeBoundaryBar(trackInfos(a, b), 'HEAD_FIRST', undefined)).toBeUndefined();
        });
    });
});
