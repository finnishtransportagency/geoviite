import { DecisionPhase, PlanPhase, PlanSource } from 'geometry/geometry-model';
import {
    LayoutState,
    LayoutStateCategory,
    LocationTrackDescriptionSuffixMode,
    LocationTrackState,
    LocationTrackType,
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

export const planSources: LocalizedEnum<PlanSource>[] = values('plan-source', [
    'GEOMETRIAPALVELU',
    'PAIKANNUSPALVELU',
]);

export const layoutStates: LocalizedEnum<LayoutState>[] = values('layout-state', [
    'IN_USE',
    'NOT_IN_USE',
    'DELETED',
]);

export const locationTrackStates: LocalizedEnum<LocationTrackState>[] = values(
    'location-track-state',
    ['BUILT', 'IN_USE', 'NOT_IN_USE', 'DELETED'],
);

export const layoutStateCategories: LocalizedEnum<LayoutStateCategory>[] = values(
    'layout-state-category',
    ['EXISTING', 'NOT_EXISTING'],
);

export const locationTrackTypes: LocalizedEnum<LocationTrackType>[] = values(
    'location-track-type',
    ['MAIN', 'SIDE', 'TRAP', 'CHORD'],
);

export const publishOperationTypes: LocalizedEnum<Operation>[] = values('publish-operation', [
    'CREATE',
    'DELETE',
    'MODIFY',
    'RESTORE',
    'CALCULATED',
]);

export const ratkoPushErrorTypes: LocalizedEnum<RatkoPushErrorType>[] = values(
    'ratko-push-error-type',
    ['PROPERTIES', 'LOCATION', 'GEOMETRY', 'STATE'],
);

export const ratkoPushErrorOperations: LocalizedEnum<RatkoPushErrorOperation>[] = values(
    'ratko-push-error-operation',
    ['CREATE', 'UPDATE', 'DELETE'],
);

export const topologicalConnectivityTypes: LocalizedEnum<TopologicalConnectivityType>[] = values(
    'topological-connectivity-type',
    ['NONE', 'START', 'END', 'START_AND_END'],
);

export const descriptionSuffixModes: LocalizedEnum<LocationTrackDescriptionSuffixMode>[] = values(
    'location-track-description-suffix',
    ['NONE', 'SWITCH_TO_SWITCH', 'SWITCH_TO_BUFFER', 'SWITCH_TO_OWNERSHIP_BOUNDARY'],
);

export const planPhases: LocalizedEnum<PlanPhase>[] = values('plan-phase', [
    'RAILWAY_PLAN',
    'RAILWAY_CONSTRUCTION_PLAN',
    'RENOVATION_PLAN',
    'ENHANCED_RENOVATION_PLAN',
    'MAINTENANCE',
    'NEW_INVESTMENT',
    'REMOVED_FROM_USE',
]);

export const planDecisionPhases: LocalizedEnum<DecisionPhase>[] = values('plan-decision', [
    'APPROVED_PLAN',
    'UNDER_CONSTRUCTION',
    'IN_USE',
]);

export const measurementMethods: LocalizedEnum<MeasurementMethod>[] = values('measurement-method', [
    'VERIFIED_DESIGNED_GEOMETRY',
    'OFFICIALLY_MEASURED_GEODETICALLY',
    'TRACK_INSPECTION',
    'DIGITIZED_AERIAL_IMAGE',
    'UNVERIFIED_DESIGNED_GEOMETRY',
]);

export const elevationMeasurementMethods: LocalizedEnum<ElevationMeasurementMethod>[] = values(
    'elevation-measurement-method',
    ['TOP_OF_SLEEPER', 'TOP_OF_RAIL'],
);

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
        value: TrapPoint.Yes,
        get name() {
            return i18n.t('enum.trap-point.Yes');
        },
        qaId: `trap-point-yes`,
    },
    {
        value: TrapPoint.No,
        get name() {
            return i18n.t('enum.trap-point.No');
        },
        qaId: `trap-point-no`,
    },
    {
        value: TrapPoint.Unknown,
        get name() {
            return i18n.t('enum.trap-point.Unknown');
        },
        qaId: `trap-point-unknown`,
    },
];

export const translateSwitchTrapPoint = (trapPoint: TrapPoint) =>
    switchTrapPoints.find((option) => option.value == trapPoint)?.name;

export function switchJointNumberToString(joint: JointNumber): string {
    return joint.substring(6);
}
