import {
    LayoutKmPostId,
    LayoutSegmentId,
    LayoutState,
    LayoutStateCategory,
    LayoutSwitchId,
    LayoutTrackNumberId,
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
    LayoutEndPoint,
    LocationAccuracy,
    LocationTrackPointUpdateType,
    SwitchAlignmentId,
    SwitchOwnerId,
    SwitchStructure,
    SwitchStructureId,
} from 'common/common-model';

export type LocationTrackSaveRequest = {
    name: string;
    description?: string | null;
    type?: LocationTrackType | null;
    state?: LayoutState;
    trackNumberId?: LayoutTrackNumberId | null;
    duplicateOf: string | null;
    topologicalConnectivity?: TopologicalConnectivityType;
};

export type LocationTrackTypeUpdateRequest = {
    updateType: LocationTrackPointUpdateType;
};

export type LocationTrackEndPointConnectedUpdateRequest = LocationTrackTypeUpdateRequest & {
    connectedLocationTrackId: LocationTrackId;
};

export type LocationTrackSaveError = {
    validationErrors: string[];
};

export type LinkingState =
    | PreliminaryLinkingGeometry
    | LinkingGeometryWithAlignment
    | LinkingGeometryWithEmptyAlignment
    | LinkingAlignment
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
    segmentId: LayoutSegmentId;
    ordering: number;
    x: number;
    y: number;
    isSegmentEndPoint: boolean;
    isEndPoint: boolean;
    direction: number | undefined;
};

export type ClusterPoint = LinkPoint & {
    layoutPoint: LinkPoint;
    geometryPoint: LinkPoint;
};

export type LinkInterval = {
    start?: LinkPoint;
    end?: LinkPoint;
};
export const emptyLinkInterval = { start: undefined, end: undefined };

type LinkingBaseType = {
    type: LinkingType;
    state: 'preliminary' | 'setup' | 'allSet';
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

export type LinkingKmPost = LinkingBaseType & {
    type: LinkingType.LinkingKmPost;
    geometryKmPostId: GeometryKmPostId;
};

export type KmPostSaveRequest = {
    kmNumber: KmNumber;
    state?: LayoutState;
    trackNumberId?: LayoutTrackNumberId | null;
};

export type KmPostSaveError = {
    validationErrors: string[];
};

export enum LinkingType {
    LinkingGeometryWithAlignment,
    LinkingAlignment,
    LinkingGeometryWithEmptyAlignment,
    LinkingSwitch,
    LinkingKmPost,
    UnknownAlignment,
}

export type PointRequest = {
    segmentId: LayoutSegmentId;
    endPointType?: LayoutEndPoint;
    point: Point;
};

export type IntervalRequest = {
    start: PointRequest;
    end: PointRequest;
    alignmentId: LocationTrackId | ReferenceLineId;
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

export function toIntervalRequest(point: LinkPoint): PointRequest {
    return {
        segmentId: point.segmentId,
        point: {
            x: point.x,
            y: point.y,
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
    layoutSwitchId: LayoutSwitchId | null;
    segmentIndex: number;
    segmentM: number;
};

export type SuggestedSwitchJoint = {
    number: JointNumber;
    location: Point;
    matches: SuggestedSwitchJointMatch[];
    locationAccuracy?: LocationAccuracy;
};

export type SuggestedSwitch = {
    name: string;
    geometryPlanId: GeometryPlanId | null;
    id: SuggestedSwitchId;
    switchStructure: SwitchStructure;
    joints: SuggestedSwitchJoint[];
    geometrySwitchId: GeometrySwitchId | null;
    alignmentEndPoint: LocationTrackEndpoint | null;
};

export type SwitchLinkingSegment = {
    locationTrackId: LocationTrackId;
    segmentIndex: number;
    segmentM: number;
};

export type SwitchLinkingJoint = {
    jointNumber: JointNumber;
    location: Point;
    segments: SwitchLinkingSegment[];
    locationAccuracy?: LocationAccuracy;
};

export type SwitchLinkingParameters = {
    geometrySwitchId: GeometrySwitchId | null;
    layoutSwitchId: LayoutSwitchId;
    joints: SwitchLinkingJoint[];
    switchStructureId: SwitchStructureId;
};

export type TrackLayoutSwitchSaveRequest = {
    name: string;
    switchStructureId: SwitchStructureId;
    stateCategory: LayoutStateCategory;
    ownerId: SwitchOwnerId;
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

export type LinkingIssue = LocationTrackEndpoint;

export type SuggestedSwitchCreateParamsAlignmentMapping = {
    switchAlignmentId: SwitchAlignmentId;
    locationTrackId: LocationTrackId;
    ascending: boolean | undefined;
};

export type SuggestedSwitchCreateParams = {
    locationTrackEndpoint: LocationTrackEndpoint;
    switchStructureId: SwitchStructureId;
    alignmentMappings: SuggestedSwitchCreateParamsAlignmentMapping[];
};
