import {
    AlignmentPoint,
    LayoutKmPostGkLocation,
    LayoutKmPostId,
    LayoutState,
    LayoutStateCategory,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutSwitchJoint,
    LayoutTrackNumberId,
    LocationTrackDescriptionSuffixMode,
    LocationTrackId,
    LocationTrackNamingScheme,
    LocationTrackSpecifier,
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
import { Point } from 'model/geometry';
import {
    JointNumber,
    KmNumber,
    LocationTrackOwnerId,
    Oid,
    Range,
    Srid,
    SwitchOwnerId,
    SwitchStructureId,
    TrackMeter,
} from 'common/common-model';
import { LayoutValidationIssue } from 'publication/publication-model';

export type LocationTrackSaveRequest = {
    namingScheme?: LocationTrackNamingScheme;
    nameFreeText?: string;
    nameSpecifier?: LocationTrackSpecifier | undefined;
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
    | PlacingLayoutSwitch
    | LinkingGeometrySwitch
    | LinkingLayoutSwitch
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
    | { type: MapAlignmentType.LocationTrack; id: LocationTrackId }
    | { type: MapAlignmentType.ReferenceLine; id: ReferenceLineId };

export type AlignmentTypeAndId =
    | LayoutAlignmentTypeAndId
    | { type: MapAlignmentType; id: GeometryAlignmentId };

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

export type LinkingGeometrySwitch = LinkingBaseType & {
    type: LinkingType.LinkingGeometrySwitch;
    suggestedSwitch: SuggestedSwitch;
    geometrySwitchId: GeometrySwitchId;
    geometryPlanId: GeometryPlanId;
    layoutSwitchId?: LayoutSwitchId;
    suggestedSwitchName: string;
};

export type LinkingLayoutSwitch = LinkingBaseType & {
    type: LinkingType.LinkingLayoutSwitch;
    suggestedSwitch: SuggestedSwitch;
    layoutSwitchId: LayoutSwitchId;
    suggestedSwitchName: string;
};

export type LinkingSwitch = LinkingGeometrySwitch | LinkingLayoutSwitch;

export type PlacingLayoutSwitch = LinkingBaseType & {
    type: LinkingType.PlacingLayoutSwitch;
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
};

export type KmPostGkFields = {
    gkSrid: Srid | undefined;
    gkLocationX: string;
    gkLocationY: string;
    gkLocationConfirmed: boolean;
};

export type KmPostSaveRequest = KmPostSimpleFields & {
    gkLocation: LayoutKmPostGkLocation | undefined;
    sourceId: GeometryKmPostId | undefined;
};

export type KmPostEditFields = KmPostSimpleFields & KmPostGkFields;

export enum LinkingType {
    LinkingGeometryWithAlignment = 'LinkingGeometryWithAlignment',
    LinkingAlignment = 'LinkingAlignment',
    LinkingGeometryWithEmptyAlignment = 'LinkingGeometryWithEmptyAlignment',
    LinkingGeometrySwitch = 'LinkingGeometrySwitch',
    LinkingLayoutSwitch = 'LinkingLayoutSwitch',
    PlacingLayoutSwitch = 'PlacingLayoutSwitch',
    LinkingKmPost = 'LinkingKmPost',
    UnknownAlignment = 'UnknownAlignment',
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

export type TopologicalJointConnection = {
    jointNumber: JointNumber;
    locationTrackIds: LocationTrackId[];
};

export type SwitchLinkingTrackLinks = {
    locationTrackVersion: number;
    suggestedLinks: SuggestedLinks | undefined;
};

export type SuggestedLinks = {
    edgeIndex: number;
    joints: SwitchLinkingJoint[];
};

export type SwitchLinkingJoint = {
    mvalueOnEdge: number;
    jointNumber: JointNumber;
    location: Point;
};

export type SuggestedSwitch = {
    joints: LayoutSwitchJoint[];
    trackLinks: Record<LocationTrackId, SwitchLinkingTrackLinks>;
};

export type GeometrySwitchSuggestionResult =
    | { failure: undefined; switch: SuggestedSwitch }
    | { failure: GeometrySwitchSuggestionFailureReason; switch: undefined };

export type SwitchLinkingParameters = {
    suggestedSwitch: SuggestedSwitch;
    geometrySwitchId?: GeometrySwitchId;
};

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

export type LayoutSwitchSaveRequest = {
    name: string;
    switchStructureId: SwitchStructureId;
    stateCategory: LayoutStateCategory;
    ownerId: SwitchOwnerId;
    trapPoint?: boolean;
    draftOid?: Oid;
};

export type SwitchRelinkingValidationResult = {
    id: LayoutSwitchId;
    successfulSuggestion?: SwitchRelinkingSuggestion;
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
