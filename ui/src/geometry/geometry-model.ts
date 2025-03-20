import { Point } from 'model/geometry';
import { LocationTrackId } from 'track-layout/track-layout-model';
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
    TrackNumber,
    VerticalCoordinateSystem,
} from 'common/common-model';
import { GeometryTypeIncludingMissing } from 'data-products/data-products-slice';
import { PVDocumentId } from 'infra-model/projektivelho/pv-model';

export type GeometryPlanLayoutId = string;
export type GeometryPlanId = string;
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
    description?: string;
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

export type PlanDecisionPhase = 'APPROVED_PLAN' | 'UNDER_CONSTRUCTION' | 'IN_USE';

export type PlanApplicability = 'PLANNING' | 'MAINTENANCE' | 'STATISTICS';
export const highestApplicability = (
    applicabilities: PlanApplicability[],
): PlanApplicability | undefined => {
    if (applicabilities.includes('PLANNING')) return 'PLANNING';
    if (applicabilities.includes('MAINTENANCE')) return 'MAINTENANCE';
    if (applicabilities.includes('STATISTICS')) return 'STATISTICS';
    return undefined;
};

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
    trackNumber?: TrackNumber;
    kmNumberRange?: KmNumberRange;
    measurementMethod: MeasurementMethod;
    elevationMeasurementMethod?: ElevationMeasurementMethod;
    planPhase: PlanPhase;
    decisionPhase: PlanDecisionPhase;
    planTime: TimeStamp;
    message?: string;
    linkedAsPlanId?: GeometryPlanId;
    uploadTime: TimeStamp;
    units: GeometryUnits;
    author: string;
    hasProfile: boolean;
    hasCant: boolean;
    isHidden: boolean;
    name: string;
    planApplicability?: PlanApplicability;
};

export type GeometryPlan = {
    id: GeometryPlanId;
    dataType: DataType;
    project: Project;
    application: Application;
    author?: Author;
    planTime?: Date;
    fileName: string;
    units: GeometryUnits;
    source: PlanSource;
    trackNumber?: TrackNumber;
    trackNumberDescription: string;
    alignments: GeometryAlignment[];
    switches: GeometrySwitch[];
    kmPosts: GeometryKmPost[];
    pvDocumentId?: PVDocumentId;
    planPhase?: PlanPhase;
    decisionPhase?: PlanDecisionPhase;
    measurementMethod?: MeasurementMethod;
    elevationMeasurementMethod?: ElevationMeasurementMethod;
    message?: string;
    uploadTime?: Date;
    isHidden: boolean;
    name: string;
    planApplicability?: PlanApplicability;
};

export enum GeometrySortBy {
    NAME,
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
    trackNumbers: TrackNumber[];
    sources: PlanSource[];
    sortBy: GeometrySortBy;
    sortOrder: GeometrySortOrder | undefined;
};

export type GeometryUnits = {
    coordinateSystemSrid?: Srid;
    coordinateSystemName?: string; // redundant if SRID is resolved
    linearUnit: LinearUnit;
    directionUnit: AngularUnit;
    verticalCoordinateSystem?: VerticalCoordinateSystem;
};

export type GeometryAlignment = {
    id: GeometryAlignmentId;
    dataType: DataType;
    name: string;
    description?: string;
    state?: PlanState;
    featureTypeCode?: string;
    elements: GeometryElement[];
    profile?: GeometryProfile;
    cant?: GeometryCant;
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
    radius?: number;
    length?: number;
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
    kmNumber?: KmNumber;
    description: string;
    state?: PlanState;
    location?: Point;
};

export type GeometrySwitch = {
    id: GeometrySwitchId;
    dataType: DataType;
    name: string;
    switchStructureId?: SwitchStructureId;
    switchTypeName: string;
    state?: PlanState;
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
    name?: string;
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
    radiusStart?: number;
    radiusEnd?: number;
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
    coordinateSystemSrid?: Srid;
    trackNumber?: TrackNumber;
    trackNumberDescription: string;
    coordinateSystemName?: string;
    connectedSwitchName: string;
    isPartial: boolean;
    planTime?: TimeStamp;
};

type LinearSection = {
    stationValueDistance?: number;
    linearSegmentLength?: number;
};

export type StationPoint = {
    address?: TrackMeter;
    height: number;
    station: number;
    location?: Point;
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
    verticalCoordinateSystem?: VerticalCoordinateSystem;
    elevationMeasurementMethod?: ElevationMeasurementMethod;
    coordinateSystemSrid?: Srid;
    coordinateSystemName?: string;

    layoutStartStation?: number;
    layoutPointStation?: number;
    layoutEndStation?: number;
};

export type VerticalGeometryDiagramDisplayItem = Omit<
    VerticalGeometryItem,
    'layoutStartStation' | 'layoutPointStation' | 'layoutEndStation' | 'start' | 'point' | 'end'
> & {
    start: CircularCurve | undefined;
    point: StationPoint | undefined;
    end: CircularCurve | undefined;
};
