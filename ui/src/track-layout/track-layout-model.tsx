import {
    GeometryAlignmentId,
    GeometryKmPostId,
    GeometryPlanId,
    GeometryPlanLayoutId,
    GeometrySwitchId,
} from 'geometry/geometry-model';
import { BoundingBox, GeometryPoint, Point } from 'model/geometry';
import {
    CoordinateSystem,
    DataType,
    JointNumber,
    KmNumber,
    LocationAccuracy,
    LocationTrackOwnerId,
    Oid,
    RowVersion,
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
import { Brand } from 'common/brand';

export type LayoutState = 'IN_USE' | 'NOT_IN_USE' | 'DELETED';
export type LocationTrackState = 'BUILT' | 'IN_USE' | 'NOT_IN_USE' | 'DELETED';
export type LayoutStateCategory = 'EXISTING' | 'NOT_EXISTING';

export const LAYOUT_SRID: Srid = 'EPSG:3067';

export type AlignmentPoint = {
    x: number;
    y: number;
    z?: number;
    m: number;
    cant?: number;
};

export function AlignmentPoint(
    x: number = 0,
    y: number = 0,
    z: number | undefined = undefined,
    m: number = 0,
    cant: number | undefined = undefined,
): AlignmentPoint {
    return {
        x: x,
        y: y,
        z: z,
        m: m,
        cant: cant,
    };
}

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

export type ReferenceLineId = Brand<string, 'ReferenceLineId'>;
export type LocationTrackId = Brand<string, 'LocationTrackId'>;
export type LocationTrackType = 'MAIN' | 'SIDE' | 'TRAP' | 'CHORD';
export type MapAlignmentSource = 'LAYOUT' | 'GEOMETRY';
export type MapAlignmentType = 'LOCATION_TRACK' | 'REFERENCE_LINE';
export type EditState = 'UNEDITED' | 'EDITED' | 'CREATED';
export type TopologicalConnectivityType = 'NONE' | 'START' | 'END' | 'START_AND_END';
export type LocationTrackDescriptionSuffixMode =
    | 'NONE'
    | 'SWITCH_TO_SWITCH'
    | 'SWITCH_TO_BUFFER'
    | 'SWITCH_TO_OWNERSHIP_BOUNDARY';

export type LayoutAssetFields = {
    version?: RowVersion;
    dataType: DataType;
    editState: EditState;
};

export type LayoutAsset =
    | LayoutReferenceLine
    | LayoutLocationTrack
    | LayoutSwitch
    | LayoutTrackNumber
    | LayoutKmPost;

export type LayoutReferenceLine = {
    id: ReferenceLineId;
    startAddress: TrackMeter;
    trackNumberId: LayoutTrackNumberId;
    boundingBox?: BoundingBox;
    length: number;
    sourceId?: GeometryAlignmentId;
    segmentCount: number;
} & LayoutAssetFields;

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
    state: LocationTrackState;
    externalId?: Oid;
    trackNumberId: LayoutTrackNumberId;
    sourceId?: GeometryAlignmentId;
    id: LocationTrackId;
    boundingBox?: BoundingBox;
    length: number;
    segmentCount: number;
    duplicateOf?: LocationTrackId;
    topologicalConnectivity: TopologicalConnectivityType;
    topologyStartSwitch?: TopologyLocationTrackSwitch;
    topologyEndSwitch?: TopologyLocationTrackSwitch;
    ownerId: LocationTrackOwnerId;
} & LayoutAssetFields;

export type DuplicateMatch = 'FULL' | 'PARTIAL' | 'NONE';

export type EndpointType = 'START' | 'END';

export type SplitPointBase = {
    location: AlignmentPoint;
    address: TrackMeter;
};

export type SwitchSplitPoint = SplitPointBase & {
    type: 'SWITCH_SPLIT_POINT';
    switchId: LayoutSwitchId;
    name: string;
};

export type EndpointSplitPoint = SplitPointBase & {
    type: 'ENDPOINT_SPLIT_POINT';
    endpointType: EndpointType;
    name: string;
};

export type SplitPoint = SwitchSplitPoint | EndpointSplitPoint;

export function splitPointsAreSame(point1: SplitPoint, point2: SplitPoint): boolean {
    switch (point1.type) {
        case 'SWITCH_SPLIT_POINT':
            return point2.type == 'SWITCH_SPLIT_POINT' && point2.switchId == point1.switchId;
        case 'ENDPOINT_SPLIT_POINT':
            return (
                point2.type == 'ENDPOINT_SPLIT_POINT' && point2.endpointType == point1.endpointType
            );
    }
}

export function SwitchSplitPoint(
    switchId: LayoutSwitchId,
    name: string,
    location: AlignmentPoint = AlignmentPoint(),
    address: TrackMeter = { kmNumber: '0000', meters: 0 },
): SwitchSplitPoint {
    return {
        type: 'SWITCH_SPLIT_POINT',
        switchId: switchId,
        name: name,
        location: location,
        address: address,
    };
}

export type DuplicateStatus = {
    match: DuplicateMatch;
    duplicateOfId?: LocationTrackId;
    startSwitchId?: LayoutSwitchId;
    endSwitchId?: LayoutSwitchId;
    startPoint?: AlignmentPoint;
    endPoint?: AlignmentPoint;
    startSplitPoint?: SplitPoint;
    endSplitPoint?: SplitPoint;
    overlappingLength?: number;
};

export type LocationTrackDuplicate = {
    id: LocationTrackId;
    trackNumberId: LayoutTrackNumberId;
    name: string;
    externalId: Oid;
    start: AlignmentPoint | undefined;
    end: AlignmentPoint | undefined;
    duplicateStatus: DuplicateStatus;
    length?: number;
};
export type LayoutSwitchIdAndName = {
    id: LayoutSwitchId;
    name: string;
};

export type LocationTrackInfoboxExtras = {
    duplicateOf?: LocationTrackDuplicate;
    duplicates: LocationTrackDuplicate[];
    startSplitPoint?: SplitPoint;
    endSplitPoint?: SplitPoint;
    switchAtStart?: LayoutSwitchIdAndName;
    switchAtEnd?: LayoutSwitchIdAndName;
    partOfUnfinishedSplit?: boolean;
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

export type LayoutSwitchId = Brand<string, 'LayoutSwitchId'>;

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
} & LayoutAssetFields;

export type LayoutSwitchJoint = {
    number: JointNumber;
    location: Point;
    locationAccuracy: LocationAccuracy;
};

export type LayoutKmPostId = Brand<string, 'LayoutKmPostId'>;

export type LayoutKmPost = {
    id: LayoutKmPostId;
    kmNumber: KmNumber;
    layoutLocation?: Point;
    gkLocation?: GeometryPoint;
    gkLocationSource?: GkLocationSource;
    gkLocationConfirmed: boolean;
    state: LayoutState;
    trackNumberId: LayoutTrackNumberId;
    sourceId?: GeometryKmPostId;
} & LayoutAssetFields;

export type GkLocationSource = 'FROM_GEOMETRY' | 'FROM_LAYOUT' | 'MANUAL';

export type LayoutKmLengthDetails = {
    trackNumber: TrackNumber;
    kmNumber: KmNumber;
    length: number;
    startM: number;
    endM: number;
    coordinateSystem: CoordinateSystem;
    layoutGeometrySource: GeometrySource;
    layoutLocation?: Point;
    gkLocation?: GeometryPoint;
    gkLocationSource?: GkLocationSource;
    gkLocationConfirmed: boolean;
    gkLocationLinkedFromGeometry: boolean;
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

export type LayoutTrackNumberId = Brand<string, 'LayoutTrackNumberId'>;

export type LayoutTrackNumber = {
    id: LayoutTrackNumberId;
    externalId?: Oid;
    description: string;
    number: TrackNumber;
    state: LayoutState;
    sourceId?: LayoutTrackNumberId;
} & LayoutAssetFields;

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
    matchDistance?: number;
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

export type OperationalPointType =
    | 'LP' // Liikennepaikka
    | 'LPO' // Liikennepaikan osa
    | 'OLP' // Osiin jaettu liikennepaikka
    | 'SEIS' // Seisake
    | 'LVH'; // Linjavaihde

export type OperatingPoint = {
    externalId: Oid;
    name: string;
    abbreviation: string;
    uicCode: string;
    type: OperationalPointType;
    location: Point;
};

export type KmPostInfoboxExtras = {
    kmLength: number | undefined;
    sourceGeometryPlanId: GeometryPlanId | undefined;
};

export function combineAlignmentPoints(points: AlignmentPoint[][]): AlignmentPoint[] {
    return deduplicateById(points.flat(), (p) => p.m).sort((p1, p2) => p1.m - p2.m);
}
