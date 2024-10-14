import {
    AlignmentPoint,
    GkLocationSource,
    LayoutKmPostId,
    LayoutState,
    LayoutStateCategory,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackDescriptionSuffixMode,
    LocationTrackId,
    LocationTrackState,
    LocationTrackType,
    MapAlignmentType,
    ReferenceLineId,
    TopologicalConnectivityType,
} from 'track-layout/track-layout-model';
import {
    GeometryAlignmentId,
    GeometryElementId,
    GeometryKmPostId,
    GeometryPlanId,
    GeometrySwitchId,
} from 'geometry/geometry-model';
import { GeometryPoint, Point } from 'model/geometry';
import {
    JointNumber,
    KmNumber,
    LocationAccuracy,
    LocationTrackOwnerId,
    Range,
    Srid,
    SwitchOwnerId,
    SwitchStructureId,
    TrackMeter,
} from 'common/common-model';
import { LayoutValidationIssue } from 'publication/publication-model';

export type LocationTrackSaveRequest = {
    name: string;
    descriptionBase?: string;
    descriptionSuffix?: LocationTrackDescriptionSuffixMode;
    type?: LocationTrackType;
    state?: LocationTrackState;
    trackNumberId?: LayoutTrackNumberId;
    duplicateOf?: string;
    topologicalConnectivity?: TopologicalConnectivityType;
    ownerId?: LocationTrackOwnerId;
};

export type LinkingState =
    | PreliminaryLinkingGeometry
    | LinkingGeometryWithAlignment
    | LinkingGeometryWithEmptyAlignment
    | LinkingAlignment
    | PlacingSwitch
    | LinkingSwitch
    | LinkingKmPost;

export type PreliminaryLinkingGeometry = {
    type: LinkingType.UnknownAlignment;
    state: 'preliminary';
    geometryPlanId: GeometryPlanId;
    geometryAlignmentId: GeometryAlignmentId;
    geometryAlignmentInterval: LinkInterval;
    layoutAlignmentInterval: LinkInterval;
};

export type LinkPointId = string;

export type LinkPoint = {
    id: LinkPointId;
    alignmentType: MapAlignmentType;
    alignmentId: LocationTrackId | ReferenceLineId | GeometryAlignmentId;
    x: number;
    y: number;
    m: number;
    isSegmentEndPoint: boolean;
    isEndPoint: boolean;
    direction: number | undefined;
    address: TrackMeter | undefined;
    isInterpolated?: boolean;
};

export type ClusterPoint = {
    id: string;
    x: number;
    y: number;
    layoutPoint: LinkPoint;
    geometryPoint: LinkPoint;
};

export type LinkInterval = {
    start?: LinkPoint;
    end?: LinkPoint;
};
export const emptyLinkInterval = { start: undefined, end: undefined };

export type MapAlignmentEndPoints = {
    start: AlignmentPoint[];
    end: AlignmentPoint[];
};

export type LinkingPhase = 'preliminary' | 'setup' | 'allSet';

type LinkingBaseType = {
    type: LinkingType;
    state: LinkingPhase;
    issues: string[];
};

export type GeometryPreliminaryLinkingParameters = {
    geometryPlanId: GeometryPlanId;
    geometryAlignmentId: GeometryAlignmentId;
};

export type GeometryLinkingAlignmentLockParameters = {
    alignment: LayoutAlignmentTypeAndId;
    type: LinkingType.LinkingGeometryWithAlignment | LinkingType.LinkingGeometryWithEmptyAlignment;
};

export type LayoutAlignmentTypeAndId =
    | { type: 'LOCATION_TRACK'; id: LocationTrackId }
    | { type: 'REFERENCE_LINE'; id: ReferenceLineId };

export type LinkingGeometryWithAlignment = LinkingBaseType & {
    type: LinkingType.LinkingGeometryWithAlignment;
    geometryPlanId: GeometryPlanId;
    layoutAlignment: LayoutAlignmentTypeAndId;
    geometryAlignmentId: GeometryAlignmentId;
    geometryAlignmentInterval: LinkInterval;
    layoutAlignmentInterval: LinkInterval;
};

export type LinkingAlignment = LinkingBaseType & {
    type: LinkingType.LinkingAlignment;
    layoutAlignment: LayoutAlignmentTypeAndId;
    layoutAlignmentInterval: LinkInterval;
};

export type LinkingGeometryWithEmptyAlignment = LinkingBaseType & {
    type: LinkingType.LinkingGeometryWithEmptyAlignment;
    geometryPlanId: GeometryPlanId;
    geometryAlignmentId: GeometryAlignmentId;
    layoutAlignment: LayoutAlignmentTypeAndId;
    geometryAlignmentInterval: LinkInterval;
};

export type LinkingSwitch = LinkingBaseType & {
    type: LinkingType.LinkingSwitch;
    suggestedSwitch: SuggestedSwitch;
    layoutSwitchId?: LayoutSwitchId;
};

export type PlacingSwitch = LinkingBaseType & {
    type: LinkingType.PlacingSwitch;
    layoutSwitch: LayoutSwitch;
    location?: Point;
};

export type LinkingKmPost = LinkingBaseType & {
    type: LinkingType.LinkingKmPost;
    geometryKmPostId: GeometryKmPostId;
};

export type KmPostSimpleFields = {
    kmNumber: KmNumber;
    state?: LayoutState;
    trackNumberId?: LayoutTrackNumberId;
    gkLocationConfirmed?: boolean;
};

export type KmPostSaveRequest = KmPostSimpleFields & {
    gkLocation: GeometryPoint | undefined;
    gkLocationSource: GkLocationSource | undefined;
    sourceId: GeometryKmPostId | undefined;
};

export type KmPostEditFields = KmPostSimpleFields & {
    gkSrid: Srid | undefined;
    gkLocationX: string;
    gkLocationY: string;
};

export enum LinkingType {
    LinkingGeometryWithAlignment,
    LinkingAlignment,
    LinkingGeometryWithEmptyAlignment,
    LinkingSwitch,
    PlacingSwitch,
    LinkingKmPost,
    UnknownAlignment,
}

export type IntervalRequest = {
    mRange: Range<number>;
    alignmentId: LocationTrackId | ReferenceLineId | GeometryAlignmentId;
};

export type LinkingGeometryWithAlignmentParameters = {
    geometryPlanId: GeometryPlanId;
    geometryInterval: IntervalRequest;
    layoutInterval: IntervalRequest;
};

export type LinkingGeometryWithEmptyAlignmentParameters = {
    geometryPlanId: GeometryPlanId;
    layoutAlignmentId: LocationTrackId | ReferenceLineId;
    geometryInterval: IntervalRequest;
};

export function toIntervalRequest(
    alignmentId: LocationTrackId | ReferenceLineId | GeometryAlignmentId,
    startM: number,
    endM: number,
): IntervalRequest {
    return {
        alignmentId: alignmentId,
        mRange: {
            min: startM,
            max: endM,
        },
    };
}

export type LinkPointType = 'geometry' | 'layout';

export type GeometryPlanLinkStatus = {
    id: GeometryPlanId;
    alignments: GeometryAlignmentLinkStatus[];
    switches: GeometrySwitchLinkStatus[];
    kmPosts: GeometryKmPostLinkStatus[];
};

export type GeometryAlignmentLinkStatus = {
    id: GeometryAlignmentId;
    elements: GeometryElementLinkStatus[];
    isLinked: boolean;
    linkedLocationTrackIds: LocationTrackId[];
    linkedReferenceLineIds: ReferenceLineId[];
};

export type GeometryElementLinkStatus = {
    id: GeometryElementId;
    isLinked: boolean;
    linkedLocationTrackIds: LocationTrackId[];
    linkedReferenceLineIds: ReferenceLineId[];
};

export type GeometrySwitchLinkStatus = {
    id: GeometrySwitchId;
    isLinked: boolean;
};

export type GeometryKmPostLinkStatus = {
    id: GeometryKmPostId;
    linkedKmPosts: LayoutKmPostId[];
};

export type SuggestedSwitchId = string;

export type SuggestedSwitchJointMatch = {
    locationTrackId: LocationTrackId;
    layoutSwitchId?: LayoutSwitchId;
    segmentIndex: number;
    m: number;
};

export type SuggestedSwitchJoint = {
    number: JointNumber;
    location: Point;
    matches: SuggestedSwitchJointMatch[];
    locationAccuracy?: LocationAccuracy;
};

export type TopologicalJointConnection = {
    jointNumber: JointNumber;
    locationTrackIds: LocationTrackId[];
};

export type TrackLayoutSwitchJoint = {
    number: JointNumber;
    location: Point;
    locationAccuracy?: LocationAccuracy;
};

export type SwitchLinkingJoint = {
    number: JointNumber;
    segmentIndex: number;
    m: number;
    location: Point;
};

export type SwitchLinkingTrackLinks = {
    segmentJoints: SwitchLinkingJoint[];
    topologyJoint?: SwitchLinkingTopologicalTrackLink;
};

export type TrackEnd = 'START' | 'END';

export type SwitchLinkingTopologicalTrackLink = {
    number: JointNumber;
    trackEnd: TrackEnd;
};

export type SuggestedSwitch = {
    id: SuggestedSwitchId;
    switchStructureId: SwitchStructureId;
    joints: TrackLayoutSwitchJoint[];
    trackLinks: Record<LocationTrackId, SwitchLinkingTrackLinks>;
    geometryPlanId?: GeometryPlanId;
    geometrySwitchId?: GeometrySwitchId;
    name: string;
};

export type GeometrySwitchSuggestionResult =
    | { switch: SuggestedSwitch }
    | { failure: GeometrySwitchSuggestionFailureReason; switch: undefined };

export type GeometrySwitchSuggestionFailureReason =
    | 'RELATED_TRACKS_NOT_LINKED'
    | 'NO_SWITCH_STRUCTURE_ID_ON_SWITCH'
    | 'NO_SRID_ON_PLAN'
    | 'INVALID_JOINTS'
    | 'LESS_THAN_TWO_JOINTS';

export type KmPostLinkingParameters = {
    geometryPlanId?: GeometryPlanId;
    geometryKmPostId: GeometryKmPostId;
    layoutKmPostId: LayoutKmPostId;
};

export type TrackLayoutSwitchSaveRequest = {
    name: string;
    switchStructureId: SwitchStructureId;
    stateCategory: LayoutStateCategory;
    ownerId: SwitchOwnerId;
    trapPoint?: boolean;
};

export type SwitchRelinkingValidationResult = {
    id: LayoutSwitchId;
    successfulSuggestion: SwitchRelinkingSuggestion;
    validationIssues: LayoutValidationIssue[];
};

export type SwitchRelinkingSuggestion = {
    location: Point;
    address: TrackMeter;
};

export type TrackSwitchRelinkingResultType = 'RELINKED' | 'NOT_AUTOMATICALLY_LINKABLE';
export type TrackSwitchRelinkingResult = {
    id: LayoutSwitchId;
    outcome: TrackSwitchRelinkingResultType;
};
