import {
    PlanApplicability,
    PlanDecisionPhase,
    PlanPhase,
    PlanSource,
} from 'geometry/geometry-model';
import {
    LayoutState,
    LayoutStateCategory,
    LocationTrackDescriptionSuffixMode,
    LocationTrackNameSpecifier,
    LocationTrackNamingScheme,
    LocationTrackState,
    LocationTrackType,
    OperationalPointState,
    RinfType,
    TopologicalConnectivityType,
    TrapPoint,
} from 'track-layout/track-layout-model';
import i18n from 'i18next';
import {
    ElevationMeasurementMethod,
    JointNumber,
    MeasurementMethod,
    VerticalCoordinateSystem,
} from 'common/common-model';
import { RatkoPushErrorOperation, RatkoPushErrorType } from 'ratko/ratko-model';
import { Operation } from 'publication/publication-model';

export interface LocalizedEnum<T> {
    value: T;

    get name(): string;

    qaId: string;
}

function values<T>(keyBase: string, enumValues: T[]): LocalizedEnum<T>[] {
    return enumValues.map((v) => ({
        value: v,
        get name() {
            return i18n.t(`enum.${keyBase}.${v}`);
        },
        qaId: `${keyBase}-${v}`,
    }));
}

function rinfTypeValues(): LocalizedEnum<RinfType>[] {
    const enumValues: RinfType[] = [
        10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150,
    ];
    return enumValues.map((v) => ({
        value: v,
        get name() {
            return i18n.t(`enum.rinf-type-full`, { rinfType: v });
        },
        qaId: `RinfType-${v}`,
    }));
}

export const planSources: LocalizedEnum<PlanSource>[] = values('PlanSource', [
    'GEOMETRIAPALVELU',
    'PAIKANNUSPALVELU',
]);

export const layoutStates: LocalizedEnum<LayoutState>[] = values('LayoutState', [
    'IN_USE',
    'NOT_IN_USE',
    'DELETED',
]);

export const locationTrackStates: LocalizedEnum<LocationTrackState>[] = values(
    'LocationTrackState',
    ['BUILT', 'IN_USE', 'NOT_IN_USE', 'DELETED'],
);

export const layoutStateCategories: LocalizedEnum<LayoutStateCategory>[] = values(
    'LayoutStateCategory',
    ['EXISTING', 'NOT_EXISTING'],
);

export const operationalPointStates: LocalizedEnum<OperationalPointState>[] = values(
    'OperationalPointState',
    ['IN_USE', 'DELETED'],
);

export const locationTrackTypes: LocalizedEnum<LocationTrackType>[] = values('LocationTrackType', [
    'MAIN',
    'SIDE',
    'TRAP',
    'CHORD',
]);

export const publishOperationTypes: LocalizedEnum<Operation>[] = values('Operation', [
    'CREATE',
    'DELETE',
    'MODIFY',
    'RESTORE',
    'CALCULATED',
]);

export const ratkoPushErrorTypes: LocalizedEnum<RatkoPushErrorType>[] = values(
    'RatkoPushErrorType',
    ['PROPERTIES', 'LOCATION', 'GEOMETRY', 'STATE'],
);

export const ratkoPushErrorOperations: LocalizedEnum<RatkoPushErrorOperation>[] = values(
    'RatkoPushErrorOperation',
    ['CREATE', 'UPDATE', 'DELETE', 'FETCH_EXISTING'],
);

export const topologicalConnectivityTypes: LocalizedEnum<TopologicalConnectivityType>[] = values(
    'TopologicalConnectivityType',
    ['NONE', 'START', 'END', 'START_AND_END'],
);

export const descriptionSuffixModes: LocalizedEnum<LocationTrackDescriptionSuffixMode>[] = values(
    'LocationTrackDescriptionSuffix',
    ['NONE', 'SWITCH_TO_SWITCH', 'SWITCH_TO_BUFFER', 'SWITCH_TO_OWNERSHIP_BOUNDARY'],
);

export const planPhases: LocalizedEnum<PlanPhase>[] = values('PlanPhase', [
    'RAILWAY_PLAN',
    'RAILWAY_CONSTRUCTION_PLAN',
    'RENOVATION_PLAN',
    'ENHANCED_RENOVATION_PLAN',
    'MAINTENANCE',
    'NEW_INVESTMENT',
    'REMOVED_FROM_USE',
]);

export const planDecisionPhases: LocalizedEnum<PlanDecisionPhase>[] = values('PlanDecisionPhase', [
    'APPROVED_PLAN',
    'UNDER_CONSTRUCTION',
    'IN_USE',
]);

export const measurementMethods: LocalizedEnum<MeasurementMethod>[] = values('MeasurementMethod', [
    'VERIFIED_DESIGNED_GEOMETRY',
    'OFFICIALLY_MEASURED_GEODETICALLY',
    'TRACK_INSPECTION',
    'DIGITIZED_AERIAL_IMAGE',
    'UNVERIFIED_DESIGNED_GEOMETRY',
]);

export const elevationMeasurementMethods: LocalizedEnum<ElevationMeasurementMethod>[] = values(
    'ElevationMeasurementMethod',
    ['TOP_OF_SLEEPER', 'TOP_OF_RAIL'],
);

export const planApplicabilities: LocalizedEnum<PlanApplicability>[] = values('PlanApplicability', [
    'PLANNING',
    'MAINTENANCE',
    'STATISTICS',
]);

export const verticalCoordinateSystems: {
    value: VerticalCoordinateSystem;
    name: string;
    qaId: string;
}[] = [
    { value: 'N43', name: 'N43', qaId: 'N43' },
    { value: 'N60', name: 'N60', qaId: 'N60' },
    { value: 'N2000', name: 'N2000', qaId: 'N2000' },
];

export const switchTrapPoints: LocalizedEnum<TrapPoint>[] = [
    {
        value: TrapPoint.YES,
        get name() {
            return i18n.t('enum.TrapPoint.YES');
        },
        qaId: `trap-point-yes`,
    },
    {
        value: TrapPoint.NO,
        get name() {
            return i18n.t('enum.TrapPoint.NO');
        },
        qaId: `trap-point-no`,
    },
    {
        value: TrapPoint.UNKNOWN,
        get name() {
            return i18n.t('enum.TrapPoint.UNKNOWN');
        },
        qaId: `trap-point-unknown`,
    },
];

export const locationTrackNamingSchemes: LocalizedEnum<LocationTrackNamingScheme>[] = values(
    'LocationTrackNamingScheme',
    [
        LocationTrackNamingScheme.FREE_TEXT,
        LocationTrackNamingScheme.WITHIN_OPERATIONAL_POINT,
        LocationTrackNamingScheme.TRACK_NUMBER_TRACK,
        LocationTrackNamingScheme.BETWEEN_OPERATIONAL_POINTS,
        LocationTrackNamingScheme.CHORD,
    ],
);

export const locationTrackNameSpecifiers: LocalizedEnum<LocationTrackNameSpecifier>[] = values(
    'LocationTrackNameSpecifier',
    [
        LocationTrackNameSpecifier.PR,
        LocationTrackNameSpecifier.ER,
        LocationTrackNameSpecifier.IR,
        LocationTrackNameSpecifier.KR,
        LocationTrackNameSpecifier.LR,
        LocationTrackNameSpecifier.PSR,
        LocationTrackNameSpecifier.ESR,
        LocationTrackNameSpecifier.ISR,
        LocationTrackNameSpecifier.LSR,
        LocationTrackNameSpecifier.PKR,
        LocationTrackNameSpecifier.EKR,
        LocationTrackNameSpecifier.IKR,
        LocationTrackNameSpecifier.LKR,
        LocationTrackNameSpecifier.ITHR,
        LocationTrackNameSpecifier.LANHR,
    ],
);

export const rinfTypes: LocalizedEnum<RinfType>[] = rinfTypeValues();

export const translateSwitchTrapPoint = (trapPoint: TrapPoint) =>
    switchTrapPoints.find((option) => option.value === trapPoint)?.name;

export function switchJointNumberToString(joint: JointNumber): string {
    return joint.substring(6);
}
