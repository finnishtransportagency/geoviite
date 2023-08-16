import {
    GeometryAlignmentId,
    GeometryKmPostId,
    GeometryPlanLayoutId,
    GeometrySwitchId,
    GeometryTrackNumberId,
} from 'geometry/geometry-model';
import { BoundingBox, Point } from 'model/geometry';
import {
    DataType,
    JointNumber,
    KmNumber,
    LocationAccuracy,
    Oid,
    Srid,
    SwitchOwnerId,
    SwitchStructureId,
    TrackMeter,
    TrackNumber,
} from 'common/common-model';
import { deduplicateById } from 'utils/array-utils';
import { AlignmentHeader, AlignmentPolyLine } from './layout-map-api';
import { GeometryPlanLinkStatus } from 'linking/linking-model';

export type LayoutState = 'IN_USE' | 'NOT_IN_USE' | 'PLANNED' | 'DELETED';
export type LayoutStateCategory = 'EXISTING' | 'NOT_EXISTING' | 'FUTURE_EXISTING';

export const LAYOUT_SRID: Srid = 'EPSG:3067';

export type LayoutPoint = {
    x: number;
    y: number;
    z: number | null;
    m: number;
    cant: number | null;
};

export type LayoutSegmentId = string;
export type GeometrySource = 'IMPORTED' | 'GENERATED' | 'PLAN';

export function filterLayoutPoints(
    desiredResolution: number,
    points: LayoutPoint[],
): LayoutPoint[] {
    let prevM = -desiredResolution;
    return points.filter((point, index) => {
        const isEndPoint = index === 0 || index === points.length - 1;
        const result = isEndPoint || point.m - prevM >= desiredResolution;
        if (result) prevM = point.m;
        return result;
    });
}

export type ReferenceLineId = string;
export type LocationTrackId = string;
export type LocationTrackType = 'MAIN' | 'SIDE' | 'TRAP' | 'CHORD';
export type MapAlignmentSource = 'LAYOUT' | 'GEOMETRY';
export type MapAlignmentType = 'LOCATION_TRACK' | 'REFERENCE_LINE';
export type DraftType = 'NEW_DRAFT' | 'EDITED_DRAFT' | 'OFFICIAL';
export type TopologicalConnectivityType = 'NONE' | 'START' | 'END' | 'START_AND_END';

export type LayoutReferenceLine = {
    id: ReferenceLineId;
    startAddress: TrackMeter;
    trackNumberId: LayoutTrackNumberId;
    boundingBox: BoundingBox | null;
    length: number;
    sourceId: GeometryAlignmentId | null;
    segmentCount: number;
    version: string | null;
    draftType: DraftType;
};

export type LayoutLocationTrackDuplicate = {
    name: string;
    externalId: Oid | null;
    id: LocationTrackId;
};

export type TopologyLocationTrackSwitch = {
    switchId: LayoutSwitchId;
    joint: JointNumber;
};

export type LayoutLocationTrack = {
    name: string;
    description: string | null;
    type: LocationTrackType | null;
    state: LayoutState;
    externalId: Oid | null;
    trackNumberId: LayoutTrackNumberId;
    sourceId: GeometryAlignmentId | null;
    id: LocationTrackId;
    dataType: DataType;
    version: string;
    boundingBox: BoundingBox | null;
    length: number;
    segmentCount: number;
    draftType: DraftType;
    duplicateOf: LocationTrackId | null;
    topologicalConnectivity: TopologicalConnectivityType;
    topologyStartSwitch: TopologyLocationTrackSwitch | null;
    topologyEndSwitch: TopologyLocationTrackSwitch | null;
};

export type AlignmentId = LocationTrackId | ReferenceLineId | GeometryAlignmentId;

export enum TrapPoint {
    Yes = 1,
    No,
    Unknown,
}

export function booleanToTrapPoint(trapPoint: boolean | null): TrapPoint {
    switch (trapPoint) {
        case true:
            return TrapPoint.Yes;
        case false:
            return TrapPoint.No;
        default:
            return TrapPoint.Unknown;
    }
}

export function trapPointToBoolean(trapPoint: TrapPoint): boolean | undefined {
    switch (trapPoint) {
        case TrapPoint.Yes:
            return true;
        case TrapPoint.No:
            return false;
        default:
            return undefined;
    }
}

export type LayoutSwitchId = string;

export type LayoutSwitch = {
    id: LayoutSwitchId;
    externalId: Oid | null;
    name: string;
    switchStructureId: SwitchStructureId;
    stateCategory: LayoutStateCategory;
    joints: LayoutSwitchJoint[];
    sourceId: GeometrySwitchId | null;
    trapPoint: boolean | null;
    ownerId: SwitchOwnerId | null;
    version: string | null;
    dataType?: DataType;
    draftType: DraftType;
};

export type LayoutSwitchJoint = {
    number: JointNumber;
    location: Point;
    locationAccuracy: LocationAccuracy;
};

export type LayoutKmPostId = string;

export type LayoutKmPost = {
    id: LayoutKmPostId;
    kmNumber: KmNumber;
    location: Point | null;
    state: LayoutState;
    trackNumberId: LayoutTrackNumberId;
    sourceId: GeometryKmPostId | null;
    version: string | null;
    draftType: DraftType;
};

export type LayoutKmLengthDetails = {
    trackNumber: TrackNumber;
    kmNumber: KmNumber;
    length: number;
    startM: number;
    endM: number;
    locationSource: GeometrySource;
    location: Point | null;
};

export type PlanAreaId = string;
export type PlanArea = {
    id: PlanAreaId;
    fileName: string;
    polygon: Point[];
};

export type GeometryPlanLayout = {
    name: string;
    alignments: PlanLayoutAlignment[];
    switches: LayoutSwitch[];
    kmPosts: LayoutKmPost[];
    boundingBox: BoundingBox;
    planId: GeometryPlanLayoutId;
    planDataType: DataType;
};

export type PlanAndStatus = {
    plan: GeometryPlanLayout;
    status: GeometryPlanLinkStatus | undefined;
};

export type PlanLayoutAlignment = {
    header: AlignmentHeader;
    polyLine: AlignmentPolyLine | null;
    segmentMValues: number[];
};

export type LayoutTrackNumberId = string;

export type LayoutTrackNumber = {
    id: LayoutTrackNumberId;
    externalId: Oid | null;
    description: string;
    number: TrackNumber;
    state: LayoutState;
    sourceId: GeometryTrackNumberId | null;
    draftType: DraftType;
};

export type AddressPoint = {
    point: LayoutPoint;
    address: TrackMeter;
};

export type AlignmentStartAndEnd = {
    start: AddressPoint | null;
    end: AddressPoint | null;
};

export function getSwitchPresentationJoint(
    layoutSwitch: LayoutSwitch,
    presentationJointNumber: JointNumber,
): LayoutSwitchJoint | undefined {
    return layoutSwitch.joints.find((joint) => joint.number == presentationJointNumber);
}

export type LayoutSwitchJointMatch = {
    locationTrackId: LocationTrackId;
    location: Point;
};

export type LayoutSwitchJointConnection = {
    number: JointNumber;
    accurateMatches: LayoutSwitchJointMatch[];
    locationAccuracy?: LocationAccuracy;
};

export type SwitchJointTrackMeter = {
    jointNumber: JointNumber;
    locationTrackId: LocationTrackId;
    locationTrackName: string;
    trackMeter: TrackMeter;
};

export function combineLayoutPoints(points: LayoutPoint[][]): LayoutPoint[] {
    return deduplicateById(points.flat(), (p) => p.m).sort((p1, p2) => p1.m - p2.m);
}
