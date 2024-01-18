import {
    GeometryAlignmentId,
    GeometryKmPostId,
    GeometryPlanId,
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
    LocationTrackOwnerId,
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
import { exhaustiveMatchingGuard } from 'utils/type-utils';

export type LayoutState = 'IN_USE' | 'NOT_IN_USE' | 'PLANNED' | 'DELETED';
export type LayoutStateCategory = 'EXISTING' | 'NOT_EXISTING' | 'FUTURE_EXISTING';

export const LAYOUT_SRID: Srid = 'EPSG:3067';

export type AlignmentPoint = {
    x: number;
    y: number;
    z?: number;
    m: number;
    cant?: number;
};

export type LayoutSegmentId = string;
export type GeometrySource = 'IMPORTED' | 'GENERATED' | 'PLAN';

export function filterAlignmentPoints(
    desiredResolution: number,
    points: AlignmentPoint[],
): AlignmentPoint[] {
    let prevM = -desiredResolution;
    return points.filter((point, index) => {
        const isEndPoint = index === 0 || index === points.length - 1;
        const result = isEndPoint || Math.round(point.m - prevM) >= desiredResolution;
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
export type LocationTrackDescriptionSuffixMode =
    | 'NONE'
    | 'SWITCH_TO_SWITCH'
    | 'SWITCH_TO_BUFFER'
    | 'SWITCH_TO_OWNERSHIP_BOUNDARY';

export type LayoutReferenceLine = {
    id: ReferenceLineId;
    startAddress: TrackMeter;
    trackNumberId: LayoutTrackNumberId;
    boundingBox?: BoundingBox;
    length: number;
    sourceId?: GeometryAlignmentId;
    segmentCount: number;
    version?: string;
    draftType: DraftType;
};

export type TopologyLocationTrackSwitch = {
    switchId: LayoutSwitchId;
    joint: JointNumber;
};

export type LocationTrackDescription = {
    id: LocationTrackId;
    description: string;
};

export type LayoutLocationTrack = {
    name: string;
    descriptionBase?: string;
    descriptionSuffix?: LocationTrackDescriptionSuffixMode;
    type?: LocationTrackType;
    state: LayoutState;
    externalId?: Oid;
    trackNumberId: LayoutTrackNumberId;
    sourceId?: GeometryAlignmentId;
    id: LocationTrackId;
    dataType: DataType;
    version: string;
    boundingBox?: BoundingBox;
    length: number;
    segmentCount: number;
    draftType: DraftType;
    duplicateOf?: LocationTrackId;
    topologicalConnectivity: TopologicalConnectivityType;
    topologyStartSwitch?: TopologyLocationTrackSwitch;
    topologyEndSwitch?: TopologyLocationTrackSwitch;
    ownerId: LocationTrackOwnerId;
};

export type LocationTrackDuplicate = {
    id: LocationTrackId;
    trackNumberId: LayoutTrackNumberId;
    name: string;
    externalId: Oid;
};
export type LayoutSwitchIdAndName = { id: LayoutSwitchId; name: string };

export type LocationTrackInfoboxExtras = {
    duplicateOf?: LocationTrackDuplicate;
    duplicates: LocationTrackDuplicate[];
    switchAtStart?: LayoutSwitchIdAndName;
    switchAtEnd?: LayoutSwitchIdAndName;
};

export type AlignmentId = LocationTrackId | ReferenceLineId | GeometryAlignmentId;

export enum TrapPoint {
    Yes = 1,
    No,
    Unknown,
}

export function booleanToTrapPoint(trapPoint: boolean | undefined): TrapPoint {
    switch (trapPoint) {
        case true:
            return TrapPoint.Yes;
        case false:
            return TrapPoint.No;
        case undefined:
            return TrapPoint.Unknown;
        default:
            return exhaustiveMatchingGuard(trapPoint);
    }
}

export function trapPointToBoolean(trapPoint: TrapPoint): boolean | undefined {
    switch (trapPoint) {
        case TrapPoint.Yes:
            return true;
        case TrapPoint.No:
            return false;
        case TrapPoint.Unknown:
            return undefined;
        default:
            return exhaustiveMatchingGuard(trapPoint);
    }
}

export type LayoutSwitchId = string;

export type LayoutSwitch = {
    id: LayoutSwitchId;
    externalId?: Oid;
    name: string;
    switchStructureId: SwitchStructureId;
    stateCategory: LayoutStateCategory;
    joints: LayoutSwitchJoint[];
    sourceId?: GeometrySwitchId;
    trapPoint?: boolean;
    ownerId?: SwitchOwnerId;
    version?: string;
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
    location?: Point;
    state: LayoutState;
    trackNumberId: LayoutTrackNumberId;
    sourceId?: GeometryKmPostId;
    version?: string;
    draftType: DraftType;
};

export type LayoutKmLengthDetails = {
    trackNumber: TrackNumber;
    kmNumber: KmNumber;
    length: number;
    startM: number;
    endM: number;
    locationSource: GeometrySource;
    location?: Point;
};

export type PlanArea = {
    id: GeometryPlanId;
    fileName: string;
    polygon: Point[];
};

export type GeometryPlanLayout = {
    name: string;
    alignments: PlanLayoutAlignment[];
    switches: LayoutSwitch[];
    kmPosts: LayoutKmPost[];
    boundingBox: BoundingBox;
    id: GeometryPlanLayoutId;
    planHidden: boolean;
    planDataType: DataType;
};

export type PlanAndStatus = {
    plan: GeometryPlanLayout;
    status?: GeometryPlanLinkStatus;
};

export type PlanLayoutAlignment = {
    header: AlignmentHeader;
    polyLine?: AlignmentPolyLine;
    segmentMValues: number[];
};

export type LayoutTrackNumberId = string;

export type LayoutTrackNumber = {
    id: LayoutTrackNumberId;
    externalId?: Oid;
    description: string;
    number: TrackNumber;
    state: LayoutState;
    sourceId?: GeometryTrackNumberId;
    draftType: DraftType;
};

export type AddressPoint = {
    point: AlignmentPoint;
    address: TrackMeter;
};

export type AlignmentStartAndEnd = {
    id: AlignmentId;
    start?: AddressPoint;
    end?: AddressPoint;
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
    trackMeter: TrackMeter | undefined;
    location: Point;
};

export function combineAlignmentPoints(points: AlignmentPoint[][]): AlignmentPoint[] {
    return deduplicateById(points.flat(), (p) => p.m).sort((p1, p2) => p1.m - p2.m);
}
