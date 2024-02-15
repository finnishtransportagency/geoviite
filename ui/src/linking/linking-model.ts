import {
    LayoutKmPostId,
    LayoutState,
    LayoutStateCategory,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackDescriptionSuffixMode,
    LocationTrackId,
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
    LocationAccuracy,
    LocationTrackOwnerId,
    LocationTrackPointUpdateType,
    Range,
    SwitchAlignmentId,
    SwitchOwnerId,
    SwitchStructure,
    SwitchStructureId,
    TrackMeter,
} from 'common/common-model';
import { PublishValidationError } from 'publication/publication-model';

export type LocationTrackSaveRequest = {
    name: string;
    descriptionBase?: string;
    descriptionSuffix?: LocationTrackDescriptionSuffixMode;
    type?: LocationTrackType;
    state?: LayoutState;
    trackNumberId?: LayoutTrackNumberId;
    duplicateOf?: string;
    topologicalConnectivity?: TopologicalConnectivityType;
    ownerId?: LocationTrackOwnerId;
};

export type LocationTrackSaveError = {
    validationErrors: string[];
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

export type LinkingPhase = 'preliminary' | 'setup' | 'allSet';

type LinkingBaseType = {
    type: LinkingType;
    state: LinkingPhase;
    errors: string[];
};

export type GeometryPreliminaryLinkingParameters = {
    geometryPlanId: GeometryPlanId;
    geometryAlignmentId: GeometryAlignmentId;
};

export type GeometryLinkingAlignmentLockParameters = {
    alignmentId: LocationTrackId | ReferenceLineId;
    alignmentType: MapAlignmentType;
    type: LinkingType.LinkingGeometryWithAlignment | LinkingType.LinkingGeometryWithEmptyAlignment;
};

export type LinkingGeometryWithAlignment = LinkingBaseType & {
    type: LinkingType.LinkingGeometryWithAlignment;
    layoutAlignmentType: MapAlignmentType;
    geometryPlanId: GeometryPlanId;
    geometryAlignmentId: GeometryAlignmentId;
    layoutAlignmentId: LocationTrackId | ReferenceLineId;
    geometryAlignmentInterval: LinkInterval;
    layoutAlignmentInterval: LinkInterval;
};

export type LinkingAlignment = LinkingBaseType & {
    type: LinkingType.LinkingAlignment;
    layoutAlignmentType: MapAlignmentType;
    layoutAlignmentId: LocationTrackId | ReferenceLineId;
    layoutAlignmentInterval: LinkInterval;
};

export type LinkingGeometryWithEmptyAlignment = LinkingBaseType & {
    type: LinkingType.LinkingGeometryWithEmptyAlignment;
    layoutAlignmentType: MapAlignmentType;
    geometryPlanId: GeometryPlanId;
    geometryAlignmentId: GeometryAlignmentId;
    layoutAlignmentId: LocationTrackId | ReferenceLineId;
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

export type KmPostSaveRequest = {
    kmNumber: KmNumber;
    state?: LayoutState;
    trackNumberId?: LayoutTrackNumberId;
};

export type KmPostSaveError = {
    validationErrors: string[];
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

export type SuggestedSwitch = {
    name: string;
    geometryPlanId?: GeometryPlanId;
    id: SuggestedSwitchId;
    switchStructure: SwitchStructure;
    joints: SuggestedSwitchJoint[];
    geometrySwitchId?: GeometrySwitchId;
    alignmentEndPoint?: LocationTrackEndpoint;
    topologicalJointConnections?: TopologicalJointConnection[];
};

export type SwitchLinkingSegment = {
    locationTrackId: LocationTrackId;
    segmentIndex: number;
    m: number;
};

export type SwitchLinkingJoint = {
    jointNumber: JointNumber;
    location: Point;
    segments: SwitchLinkingSegment[];
    locationAccuracy?: LocationAccuracy;
};

export type SwitchLinkingParameters = {
    geometryPlanId?: GeometryPlanId;
    geometrySwitchId?: GeometrySwitchId;
    layoutSwitchId: LayoutSwitchId;
    joints: SwitchLinkingJoint[];
    switchStructureId: SwitchStructureId;
};
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

export type TrackLayoutSaveError = {
    validationErrors: string[];
};

export type LocationTrackEndpoint = {
    // "id" generated at runtime and is for UI only
    id: string;
    locationTrackId: LocationTrackId;
    location: Point;
    updateType: LocationTrackPointUpdateType;
};

export type SuggestedSwitchCreateParamsAlignmentMapping = {
    switchAlignmentId: SwitchAlignmentId;
    locationTrackId: LocationTrackId;
    ascending?: boolean;
};

export type SuggestedSwitchCreateParams = {
    locationTrackEndpoint: LocationTrackEndpoint;
    switchStructureId: SwitchStructureId;
    alignmentMappings: SuggestedSwitchCreateParamsAlignmentMapping[];
};

export type SwitchRelinkingValidationResult = {
    id: LayoutSwitchId;
    successfulSuggestion: SwitchRelinkingSuggestion;
    validationErrors: PublishValidationError[];
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
