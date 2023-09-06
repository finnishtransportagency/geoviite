import { Point } from 'model/geometry';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import {
    AngularUnit,
    DataType,
    ElementLocation,
    ElevationMeasurementMethod,
    JointNumber,
    KmNumber,
    LinearUnit,
    MeasurementMethod,
    RotationDirection,
    Srid,
    SwitchStructureId,
    TimeStamp,
    TrackMeter,
    VerticalCoordinateSystem,
} from 'common/common-model';
import { GeometryTypeIncludingMissing } from 'data-products/data-products-slice';
import { PVDocumentId } from 'infra-model/projektivelho/pv-model';

export type GeometryPlanLayoutId = string;
export type GeometryPlanId = string;
export type GeometryTrackNumberId = string;
export type GeometryAlignmentId = string;
export type GeometryElementId = string;
export type GeometrySwitchId = string;
export type GeometryKmPostId = string;

export type PlanSource = 'GEOMETRIAPALVELU' | 'PAIKANNUSPALVELU';
export type PlanState = 'ABANDONED' | 'DESTROYED' | 'EXISTING' | 'PROPOSED';
export type CantTransitionType = 'LINEAR' | 'BIQUADRATIC_PARABOLA';

export type Project = {
    id: ProjectId;
    name: string;
    description: string | null;
};

export type ProjectId = string;

export type AuthorId = string;

export type Application = {
    id: string;
    name: string;
    manufacturer: string;
    version: string;
};

export type Author = {
    id: AuthorId;
    companyName: string;
};

export type KmNumberRange = {
    min: KmNumber;
    max: KmNumber;
};

export type DecisionPhase = 'APPROVED_PLAN' | 'UNDER_CONSTRUCTION' | 'IN_USE';

export type PlanPhase =
    | 'RAILWAY_PLAN'
    | 'RAILWAY_CONSTRUCTION_PLAN'
    | 'RENOVATION_PLAN'
    | 'ENHANCED_RENOVATION_PLAN'
    | 'MAINTENANCE'
    | 'NEW_INVESTMENT'
    | 'REMOVED_FROM_USE';

export type GeometryPlanHeader = {
    id: GeometryPlanId;
    project: Project;
    fileName: string;
    source: PlanSource;
    trackNumberId: LayoutTrackNumberId | undefined;
    kmNumberRange: KmNumberRange | undefined;
    measurementMethod: MeasurementMethod;
    elevationMeasurementMethod: ElevationMeasurementMethod | null;
    planPhase: PlanPhase;
    decisionPhase: DecisionPhase;
    planTime: TimeStamp;
    message: string | null;
    linkedAsPlanId: GeometryPlanId | null;
    uploadTime: TimeStamp;
    units: GeometryUnits;
    author: string;
    hasProfile: boolean;
    hasCant: boolean;
    isHidden: boolean;
};

export type GeometryPlan = {
    id: GeometryPlanId;
    dataType: DataType;
    project: Project;
    application: Application;
    author: Author | null;
    planTime: Date | undefined;
    fileName: string;
    units: GeometryUnits;
    source: PlanSource;
    trackNumberId: LayoutTrackNumberId | undefined;
    trackNumberDescription: string;
    alignments: GeometryAlignment[];
    switches: GeometrySwitch[];
    kmPosts: GeometryKmPost[];
    pvDocumentId: PVDocumentId | null;
    planPhase: PlanPhase | null;
    decisionPhase: DecisionPhase | null;
    measurementMethod: MeasurementMethod | null;
    elevationMeasurementMethod: ElevationMeasurementMethod | null;
    message: string | null;
    uploadTime: Date | null;
    isHidden: boolean;
};

export enum GeometrySortBy {
    PROJECT_NAME,
    TRACK_NUMBER,
    KM_START,
    KM_END,
    PLAN_PHASE,
    DECISION_PHASE,
    CREATED_AT,
    UPLOADED_AT,
    NO_SORTING,
    FILE_NAME,
    LINKED_AT,
    LINKED_BY,
}

export enum GeometrySortOrder {
    ASCENDING,
    DESCENDING,
}

export type GeometryPlanSearchParams = {
    freeText: string;
    trackNumberIds: LayoutTrackNumberId[];
    sources: PlanSource[];
    sortBy: GeometrySortBy;
    sortOrder: GeometrySortOrder | undefined;
};

export type GeometryUnits = {
    coordinateSystemSrid: Srid | null;
    coordinateSystemName: string | null; // redundant if SRID is resolved
    linearUnit: LinearUnit;
    directionUnit: AngularUnit;
    verticalCoordinateSystem: VerticalCoordinateSystem | null;
};

export type GeometryAlignment = {
    id: GeometryAlignmentId;
    dataType: DataType;
    name: string;
    description: string | null;
    state: PlanState | null;
    featureTypeCode: string | null;
    elements: GeometryElement[];
    profile: GeometryProfile | null;
    cant: GeometryCant | null;
    trackNumberId: GeometryTrackNumberId;
};

export type GeometryProfile = {
    name: string;
    elements: GeometryVerticalIntersection[];
};

export type GeometryVIBase = {
    description: string;
    point: Point;
};
export type ViPoint = GeometryVIBase;
export type ViCircularCurve = {
    radius: number | null;
    length: number | null;
} & GeometryVIBase;
export type GeometryVerticalIntersection = ViPoint | ViCircularCurve;

export type GeometryCant = {
    name: string;
    description: string;
    points: CantPoint[];
};

export type CantPoint = {
    station: number;
    appliedCant: number;
    curvature: RotationDirection;
    transitionType: CantTransitionType;
};

export type GeometryKmPost = {
    id: GeometryKmPostId;
    dataType: DataType;
    kmNumber: KmNumber | null;
    description: string;
    state: PlanState | null;
    location: Point | null;
    trackNumberId: GeometryTrackNumberId;
};

export type GeometrySwitch = {
    id: GeometrySwitchId;
    dataType: DataType;
    name: string;
    switchStructureId: SwitchStructureId | null;
    switchTypeName: string;
    state: PlanState | null;
    joints: GeometrySwitchJoint[];
};
export type GeometrySwitchJoint = {
    number: JointNumber;
    location: Point;
};

export const enum GeometryType {
    LINE = 'LINE',
    CURVE = 'CURVE',
    BIQUADRATIC_PARABOLA = 'BIQUADRATIC_PARABOLA',
    CLOTHOID = 'CLOTHOID',
}

export type GeometryElementBase = {
    id: GeometryElementId;
    type: GeometryType;
    name: string | null;
    start: Point;
    end: Point;
    calculatedLength: number;
};

export type GeometryLine = GeometryElementBase;

export type GeometryCurve = {
    rotation: RotationDirection;
    radius: number;
    chord: number;
    center: Point;
} & GeometryElementBase;

export type GeometrySpiral = {
    rotation: RotationDirection;
    radiusStart: number | null;
    radiusEnd: number | null;
    pi: Point;
} & GeometryElementBase;

export type GeometryBiquadraticParabola = GeometrySpiral;

export type GeometryClothoid = {
    constant: number;
} & GeometrySpiral;

export type GeometryElement =
    | GeometryLine
    | GeometryCurve
    | GeometryBiquadraticParabola
    | GeometryClothoid;

export type ElementItem = {
    id: string;
    alignmentId: LocationTrackId;
    alignmentName: string;
    locationTrackName: string;
    elementId: GeometryElementId;
    elementType: GeometryTypeIncludingMissing;
    start: ElementLocation;
    end: ElementLocation;
    lengthMeters: number;
    planId: GeometryPlanId;
    planSource: PlanSource;
    fileName: string;
    coordinateSystemSrid: Srid | null;
    trackNumberId: LayoutTrackNumberId;
    trackNumberDescription: string;
    coordinateSystemName: string | null;
    connectedSwitchName: string;
    isPartial: boolean;
};

type LinearSection = {
    stationValueDistance: number | null;
    linearSegmentLength: number | null;
};

export type StationPoint = {
    address: TrackMeter | null;
    height: number;
    station: number;
    location: Point | null;
};

type CircularCurve = StationPoint & {
    angle: number;
};

export type VerticalGeometryItem = {
    alignmentId: string;
    id: string;
    planId: GeometryPlanId;
    creationTime: TimeStamp;
    fileName: string;
    alignmentName: string;
    start: CircularCurve;
    point: StationPoint;
    end: CircularCurve;
    radius: number;
    tangent: number;
    linearSectionBackward: LinearSection;
    linearSectionForward: LinearSection;
    locationTrackName: string;
    overlapsAnother: boolean;
    verticalCoordinateSystem: VerticalCoordinateSystem | null;
    elevationMeasurementMethod: ElevationMeasurementMethod | null;
    coordinateSystemSrid: Srid | null;
    coordinateSystemName: string | null;

    layoutStartStation: number | null;
    layoutPointStation: number | null;
    layoutEndStation: number | null;
};
