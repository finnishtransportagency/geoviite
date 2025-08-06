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
    LayoutContext,
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
import { AlignmentPolyLine, GeometryAlignmentHeader } from './layout-map-api';
import { GeometryPlanLinkStatus } from 'linking/linking-model';
import { exhaustiveMatchingGuard, ifDefined } from 'utils/type-utils';
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
export type TopologicalConnectivityType = 'NONE' | 'START' | 'END' | 'START_AND_END';
export type LocationTrackDescriptionSuffixMode =
    | 'NONE'
    | 'SWITCH_TO_SWITCH'
    | 'SWITCH_TO_BUFFER'
    | 'SWITCH_TO_OWNERSHIP_BOUNDARY';

export enum MapAlignmentType {
    LocationTrack = 'LOCATION_TRACK',
    ReferenceLine = 'REFERENCE_LINE',
}

export type LayoutAssetFields = {
    version?: RowVersion;
    dataType: DataType;
    isDraft: boolean;
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

export enum LocationTrackNamingScheme {
    FREE_TEXT = 'FREE_TEXT',
    WITHIN_OPERATING_POINT = 'WITHIN_OPERATING_POINT',
    BETWEEN_OPERATING_POINTS = 'BETWEEN_OPERATING_POINTS',
    TRACK_NUMBER_TRACK = 'TRACK_NUMBER_TRACK',
    CHORD = 'CHORD',
}

export enum LocationTrackNameSpecifier {
    PR = 'PR',
    ER = 'ER',
    IR = 'IR',
    KR = 'KR',
    LR = 'LR',
    PSR = 'PSR',
    ESR = 'ESR',
    ISR = 'ISR',
    LSR = 'LSR',
    PKR = 'PKR',
    EKR = 'EKR',
    IKR = 'IKR',
    LKR = 'LKR',
    ITHR = 'ITHR',
    LANHR = 'LANHR',
}

export type LocationTrackNameFreeText = {
    scheme: LocationTrackNamingScheme.FREE_TEXT;
    freeText: string;
};
export type LocationTrackNameWithinOperatingPoint = {
    scheme: LocationTrackNamingScheme.WITHIN_OPERATING_POINT;
    freeText: string;
};
export type LocationTrackNameByTrackNumber = {
    scheme: LocationTrackNamingScheme.TRACK_NUMBER_TRACK;
    freeText: string;
    specifier: LocationTrackNameSpecifier;
};
export type LocationTrackNameBetweenOperatingPoints = {
    scheme: LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS;
    specifier: LocationTrackNameSpecifier;
};
export type LocationTrackNameChord = {
    scheme: LocationTrackNamingScheme.CHORD;
};

export type LocationTrackNameStructure =
    | LocationTrackNameFreeText
    | LocationTrackNameWithinOperatingPoint
    | LocationTrackNameByTrackNumber
    | LocationTrackNameBetweenOperatingPoints
    | LocationTrackNameChord;

export function getNameFreeText(
    nameStructure: LocationTrackNameStructure | undefined,
): string | undefined {
    return nameStructure && 'freeText' in nameStructure ? nameStructure.freeText : undefined;
}

export function getNameSpecifier(
    nameStructure: LocationTrackNameStructure | undefined,
): LocationTrackNameSpecifier | undefined {
    return nameStructure && 'specifier' in nameStructure ? nameStructure.specifier : undefined;
}

export type LocationTrackDescriptionStructure = {
    base?: string;
    suffix?: LocationTrackDescriptionSuffixMode;
};

export type LayoutLocationTrack = {
    nameStructure: LocationTrackNameStructure;
    name: string;
    descriptionStructure: LocationTrackDescriptionStructure;
    description: string;
    type: LocationTrackType;
    state: LocationTrackState;
    trackNumberId: LayoutTrackNumberId;
    sourceId?: GeometryAlignmentId;
    id: LocationTrackId;
    boundingBox?: BoundingBox;
    length: number;
    segmentCount: number;
    startSwitchId?: LayoutSwitchId;
    endSwitchId?: LayoutSwitchId;
    duplicateOf?: LocationTrackId;
    topologicalConnectivity: TopologicalConnectivityType;
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
            return point2.type === 'SWITCH_SPLIT_POINT' && point2.switchId === point1.switchId;
        case 'ENDPOINT_SPLIT_POINT':
            return (
                point2.type === 'ENDPOINT_SPLIT_POINT' &&
                point2.endpointType === point1.endpointType
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
    start: AlignmentPoint | undefined;
    end: AlignmentPoint | undefined;
    duplicateStatus: DuplicateStatus;
    length: number;
};

export type LocationTrackInfoboxExtras = {
    duplicateOf?: LocationTrackDuplicate;
    duplicates: LocationTrackDuplicate[];
    startSplitPoint?: SplitPoint;
    endSplitPoint?: SplitPoint;
    partOfUnfinishedSplit?: boolean;
};

export type AlignmentId = LocationTrackId | ReferenceLineId | GeometryAlignmentId;

export enum TrapPoint {
    YES = 1,
    NO,
    UNKNOWN,
}

export function booleanToTrapPoint(trapPoint: boolean | undefined): TrapPoint {
    switch (trapPoint) {
        case true:
            return TrapPoint.YES;
        case false:
            return TrapPoint.NO;
        case undefined:
            return TrapPoint.UNKNOWN;
        default:
            return exhaustiveMatchingGuard(trapPoint);
    }
}

export function trapPointToBoolean(trapPoint: TrapPoint): boolean | undefined {
    switch (trapPoint) {
        case TrapPoint.YES:
            return true;
        case TrapPoint.NO:
            return false;
        case TrapPoint.UNKNOWN:
            return undefined;
        default:
            return exhaustiveMatchingGuard(trapPoint);
    }
}

export type LayoutSwitchId = Brand<string, 'LayoutSwitchId'>;

export type SwitchNameParts = {
    prefix: string;
    shortNumberPart: string;
};

export type LayoutSwitch = {
    id: LayoutSwitchId;
    name: string;
    nameParts?: SwitchNameParts;
    switchStructureId: SwitchStructureId;
    stateCategory: LayoutStateCategory;
    joints: LayoutSwitchJoint[];
    sourceId?: GeometrySwitchId;
    trapPoint?: boolean;
    ownerId?: SwitchOwnerId;
    draftOid?: Oid;
} & LayoutAssetFields;

export type SwitchJointRole = 'MAIN' | 'CONNECTION' | 'MATH';

export type LayoutSwitchJoint = {
    number: JointNumber;
    location: Point;
    role: SwitchJointRole;
    locationAccuracy?: LocationAccuracy;
};

export type LayoutKmPostGkLocation = {
    location: GeometryPoint;
    source: KmPostGkLocationSource;
    confirmed: boolean;
};

export type LayoutKmPostId = Brand<string, 'LayoutKmPostId'>;

export type LayoutKmPost = {
    id: LayoutKmPostId;
    kmNumber: KmNumber;
    layoutLocation?: Point;
    gkLocation: LayoutKmPostGkLocation | undefined;
    state: LayoutState;
    trackNumberId: LayoutTrackNumberId;
    sourceId?: GeometryKmPostId;
} & LayoutAssetFields;

export type KmPostGkLocationSource = 'FROM_GEOMETRY' | 'FROM_LAYOUT' | 'MANUAL';

export type LayoutKmLengthDetails = {
    trackNumber: TrackNumber;
    kmNumber: KmNumber;
    length: number;
    startM: number;
    endM: number;
    coordinateSystem: CoordinateSystem;
    layoutGeometrySource: GeometrySource;
    layoutLocation?: Point;
    gkLocation: LayoutKmPostGkLocation | undefined;
    gkLocationLinkedFromGeometry: boolean;
};

export type PlanArea = {
    id: GeometryPlanId;
    name: string;
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
    header: GeometryAlignmentHeader;
    polyLine?: AlignmentPolyLine;
    segmentMValues: number[];
};

export type LayoutTrackNumberId = Brand<string, 'LayoutTrackNumberId'>;

export type LayoutTrackNumber = {
    id: LayoutTrackNumberId;
    description: string;
    number: TrackNumber;
    state: LayoutState;
    sourceId?: LayoutTrackNumberId;
} & LayoutAssetFields;

export type AddressPoint = {
    point: AlignmentPoint;
    address: TrackMeter;
};

export type AlignmentEndPoint = {
    point: AlignmentPoint;
    address?: TrackMeter;
};
export type AlignmentStartAndEnd = {
    id: AlignmentId;
    start?: AlignmentEndPoint;
    end?: AlignmentEndPoint;
    staStart?: number;
};

export function getSwitchPresentationJoint(
    layoutSwitch: LayoutSwitch,
    presentationJointNumber: JointNumber,
): LayoutSwitchJoint | undefined {
    return layoutSwitch.joints.find((joint) => joint.number === presentationJointNumber);
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

export type SwitchLink = {
    id: LayoutSwitchId;
    jointNumber: JointNumber;
    jointRole: SwitchJointRole;
};

export type LayoutNodeType = 'SWITCH' | 'TRACK_START' | 'TRACK_END';
export type LayoutNodeId = Brand<string, 'LayoutNodeId'>;
export type LayoutNode = {
    id: LayoutNodeId;
    type: LayoutNodeType;
    location: Point;
    switches: SwitchLink[];
};

export type LayoutEdgeId = Brand<string, 'LayoutEdgeId'>;
export type LayoutEdge = {
    id: LayoutEdgeId;
    startNode: LayoutNodeId;
    endNode: LayoutNodeId;
    length: number;
    tracks: LocationTrackId[];
};

export type LayoutGraphLevel = 'NANO' | 'MICRO';

export type LayoutGraph = {
    nodes: { [key in LayoutNodeId]: LayoutNode };
    edges: { [key in LayoutEdgeId]: LayoutEdge };
    context: LayoutContext;
    detailLevel: LayoutGraphLevel;
};

/**
 * This function duplicates the backend logic in LocationTrack.kt for front-end formatting
 * so that UI-side validation and display logic can act independently.
 */
export function formatTrackName(
    namingScheme: LocationTrackNamingScheme,
    nameFreeText: string | undefined,
    nameSpecifier: LocationTrackNameSpecifier | undefined,
    trackNumber: TrackNumber | undefined,
    startSwitch: SwitchNameParts | undefined,
    endSwitch: SwitchNameParts | undefined,
): string {
    switch (namingScheme) {
        case LocationTrackNamingScheme.FREE_TEXT:
            return nameFreeText ?? '';
        case LocationTrackNamingScheme.WITHIN_OPERATING_POINT:
            return nameFreeText ?? '';
        case LocationTrackNamingScheme.TRACK_NUMBER_TRACK:
            return `${withPlaceholder(trackNumber)} ${toProperForm(nameSpecifier)} ${nameFreeText ?? ''}`.trim();
        case LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS:
            return `${toProperForm(nameSpecifier)} ${getShortName(startSwitch)}-${getShortName(endSwitch)}`;
        case LocationTrackNamingScheme.CHORD: {
            if (startSwitch !== undefined && startSwitch?.prefix === endSwitch?.prefix) {
                return `${startSwitch.prefix} ${getShortNumber(startSwitch)}-${getShortNumber(endSwitch)}`;
            } else {
                return `${getShortName(startSwitch)}-${getShortName(endSwitch)}`;
            }
        }
        default:
            return exhaustiveMatchingGuard(namingScheme);
    }
}

/**
 * This function duplicates the backend logic in LocationTrack.kt for front-end formatting
 * so that UI-side validation and display logic can act independently.
 */
export function formatTrackDescription(
    descriptionBase: string,
    descriptionSuffix: LocationTrackDescriptionSuffixMode,
    startSwitch: SwitchNameParts | undefined,
    endSwitch: SwitchNameParts | undefined,
    t: (key: string, params?: Record<string, unknown>) => string,
): string {
    switch (descriptionSuffix) {
        case 'NONE':
            return descriptionBase;
        case 'SWITCH_TO_BUFFER':
            return `${descriptionBase} ${getShortName(startSwitch ?? endSwitch)} - ${t('location-track-dialog.buffer')}`;
        case 'SWITCH_TO_OWNERSHIP_BOUNDARY':
            return `${descriptionBase} ${getShortName(startSwitch ?? endSwitch)} - ${t('location-track-dialog.ownership-boundary')}`;
        case 'SWITCH_TO_SWITCH':
            return `${descriptionBase} ${getShortName(startSwitch)} - ${getShortName(endSwitch)}`;
        default:
            return exhaustiveMatchingGuard(descriptionSuffix);
    }
}

function withPlaceholder(value: string | undefined): string {
    return value ?? '???';
}

function getShortNumber(layoutSwitch: SwitchNameParts | undefined): string {
    return withPlaceholder(layoutSwitch?.shortNumberPart);
}

function getShortName(layoutSwitch: SwitchNameParts | undefined): string {
    return withPlaceholder(
        ifDefined(layoutSwitch, (parsed) => `${parsed.prefix} ${parsed.shortNumberPart}`),
    );
}

// TODO: GVT-3169 Use localization for this? If here, then also in the backend?
//  Note, that this also affects split name formatting which happens in store, so translations are not easily available.
// function toProperForm(
//     specifier: LocationTrackSpecifier | undefined,
//     t: (key: string, params?: Record<string, unknown>) => string,
// ): string {
//     return specifier
//         ? t(`location-track-dialog.name-specifiers.${specifier}`)
//         : withPlaceholder(undefined);
// }
function toProperForm(specifier: LocationTrackNameSpecifier | undefined): string {
    switch (specifier) {
        case undefined:
            return withPlaceholder(undefined);
        case LocationTrackNameSpecifier.PSR:
            return 'PsR';
        case LocationTrackNameSpecifier.ESR:
            return 'EsR';
        case LocationTrackNameSpecifier.ISR:
            return 'IsR';
        case LocationTrackNameSpecifier.LSR:
            return 'LsR';
        case LocationTrackNameSpecifier.PKR:
            return 'PKR';
        case LocationTrackNameSpecifier.EKR:
            return 'EKR';
        case LocationTrackNameSpecifier.IKR:
            return 'IKR';
        case LocationTrackNameSpecifier.LKR:
            return 'LKR';
        case LocationTrackNameSpecifier.ITHR:
            return 'ItHR';
        case LocationTrackNameSpecifier.LANHR:
            return 'LänHR';
        default:
            return LocationTrackNameSpecifier[specifier];
    }
}
