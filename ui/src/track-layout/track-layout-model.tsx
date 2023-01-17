import {
    GeometryAlignmentId,
    GeometryElementId,
    GeometryKmPostId,
    GeometryPlanLayoutId,
    GeometrySwitchId,
    GeometryTrackNumberId,
} from 'geometry/geometry-model';
import { BoundingBox, combineBoundingBoxes, Point } from 'model/geometry';
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
import { filterNotEmpty } from 'utils/array-utils';

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

export type MapSegment = {
    pointCount: number;
    points: LayoutPoint[];
    sourceId: GeometryElementId | null;
    sourceStart: number | null;
    boundingBox: BoundingBox | null;
    resolution: number;
    start: number;
    length: number;
    id: LayoutSegmentId;
};

export function simplifySegments(
    idBase: string,
    segments: MapSegment[],
    resolution: number,
): MapSegment {
    const lengths = segments.map((s) => s.length);
    return {
        id: `${idBase}_${segments[0].id}_${segments[segments.length - 1].id}_${
            segments.length
        }_${resolution}`,
        resolution: Math.ceil(Math.max(...lengths)),
        pointCount: segments.map((s) => s.pointCount).reduce((v, acc) => v + acc, 0),
        points: pickSegmentPoints(segments[0].resolution, resolution, joinSegmentPoints(segments)),
        boundingBox: combineBoundingBoxes(
            segments.map((s) => s.boundingBox).filter(filterNotEmpty),
        ),
        sourceId: null,
        sourceStart: null,
        start: segments[0].start,
        length: lengths.reduce((prev, current) => prev + current, 0),
    };
}

export function simplifySegment(segment: MapSegment, resolution: number): MapSegment {
    return {
        ...segment,
        points: pickSegmentPoints(segment.resolution, resolution, segment.points),
    };
}

function joinSegmentPoints(segments: MapSegment[]): LayoutPoint[] {
    return segments.flatMap((segment, segmentIndex) =>
        segmentIndex == 0
            ? segment.points
            : segment.points.filter((_point, pointIndex) => pointIndex > 0),
    );
}

function pickSegmentPoints(
    segmentResolution: number,
    desiredResolution: number,
    points: LayoutPoint[],
): LayoutPoint[] {
    const divisor = Math.floor(
        desiredResolution < segmentResolution ? 1 : desiredResolution / segmentResolution,
    );
    if (divisor < 2) return points;
    else
        return points.filter((_value, index) => {
            return index === points.length - 1 || index % divisor == 0;
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
};

export type AlignmentId = LocationTrackId | ReferenceLineId | GeometryAlignmentId;
export type MapAlignment = {
    name: string;
    description: string | null;
    alignmentSource: MapAlignmentSource;
    alignmentType: MapAlignmentType;
    type: LocationTrackType | null;
    state: LayoutState;
    segments: MapSegment[];
    trackNumberId: LayoutTrackNumberId | null;
    sourceId: GeometryAlignmentId | null;
    id: AlignmentId;
    boundingBox: BoundingBox | null;
    length: number;
    dataType: DataType;
    segmentCount: number;
    version: string;
    draftType: DraftType;
    topologicalConnectivity: TopologicalConnectivityType;
};

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

export type PlanAreaId = string;
export type PlanArea = {
    id: PlanAreaId;
    fileName: string;
    polygon: Point[];
};

export type GeometryPlanLayout = {
    name: string;
    alignments: MapAlignment[];
    switches: LayoutSwitch[];
    kmPosts: LayoutKmPost[];
    boundingBox: BoundingBox;
    planId: GeometryPlanLayoutId;
    planDataType: DataType;
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
    distance: number;
};

export type AlignmentStartAndEnd = {
    start: AddressPoint | null;
    end: AddressPoint | null;
};

export type TrafficOperatingPointId = string;

export type TrafficOperatingPoint = {
    id: TrafficOperatingPointId;
    name: string;
    abbreviation: string;
};

export function toReferenceLine({
    id,
    alignmentType,
    trackNumberId,
    boundingBox,
    length,
    sourceId,
    segmentCount,
    version,
    draftType,
}: MapAlignment): LayoutReferenceLine | undefined {
    if (alignmentType === 'REFERENCE_LINE' && trackNumberId != null)
        return {
            id,
            trackNumberId,
            boundingBox,
            // TODO: We could add the actual address to LayoutAlignment, but these methods are backwards anyhow. We should just remove them entirely
            startAddress: {
                kmNumber: '0000',
                meters: 0.0,
            },
            length,
            sourceId,
            segmentCount,
            version,
            draftType,
        };
    else return undefined;
}

export function toLocationTrack({
    id,
    name,
    description,
    type,
    alignmentType,
    state,
    dataType,
    trackNumberId,
    boundingBox,
    length,
    sourceId,
    segmentCount,
    version,
    draftType,
    topologicalConnectivity,
}: MapAlignment): LayoutLocationTrack | undefined {
    if (alignmentType === 'LOCATION_TRACK' && trackNumberId)
        return {
            id,
            name,
            description,
            type,
            state,
            externalId: null,
            dataType,
            trackNumberId,
            boundingBox,
            length,
            sourceId,
            segmentCount,
            version,
            draftType,
            duplicateOf: null,
            topologicalConnectivity: topologicalConnectivity,
        };
    else return undefined;
}

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
