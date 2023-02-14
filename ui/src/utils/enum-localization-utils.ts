import { DecisionPhase, PlanPhase, PlanSource } from 'geometry/geometry-model';
import {
    LayoutState,
    LayoutStateCategory,
    LocationTrackType,
    TopologicalConnectivityType,
    TrapPoint,
} from 'track-layout/track-layout-model';
import i18n from 'i18next';
import { JointNumber, MeasurementMethod, VerticalCoordinateSystem } from 'common/common-model';
import { RatkoPushErrorOperation, RatkoPushErrorType } from 'ratko/ratko-model';
import { Operation } from 'publication/publication-model';

export interface LocalizedEnum<T> {
    value: T;
    name: string;
}

function values<T>(keyBase: string, enumValues: T[]): LocalizedEnum<T>[] {
    return enumValues.map((v) => ({
        value: v,
        name: i18n.t(`enum.${keyBase}.${v}`),
    }));
}

export const planSources: LocalizedEnum<PlanSource>[] = values('plan-source', [
    'GEOMETRIAPALVELU',
    'PAIKANNUSPALVELU',
]);

export const layoutStates: { value: LayoutState; name: string }[] = [
    { value: 'PLANNED', name: i18n.t('enum.layout-state.PLANNED') },
    { value: 'IN_USE', name: i18n.t('enum.layout-state.IN_USE') },
    { value: 'NOT_IN_USE', name: i18n.t('enum.layout-state.NOT_IN_USE') },
    { value: 'DELETED', name: i18n.t('enum.layout-state.DELETED') },
];

export const layoutStateCategories: { value: LayoutStateCategory; name: string }[] = [
    { value: 'FUTURE_EXISTING', name: i18n.t('enum.layout-state-category.FUTURE_EXISTING') },
    { value: 'EXISTING', name: i18n.t('enum.layout-state-category.EXISTING') },
    { value: 'NOT_EXISTING', name: i18n.t('enum.layout-state-category.NOT_EXISTING') },
];

export const locationTrackTypes: { value: LocationTrackType; name: string }[] = [
    { value: 'MAIN', name: i18n.t('enum.location-track-type.MAIN') },
    { value: 'SIDE', name: i18n.t('enum.location-track-type.SIDE') },
    { value: 'TRAP', name: i18n.t('enum.location-track-type.TRAP') },
    { value: 'CHORD', name: i18n.t('enum.location-track-type.CHORD') },
];

export const publishOperationTypes: { value: Operation; name: string }[] = [
    { value: 'CREATE', name: i18n.t('enum.publish-operation.CREATE') },
    { value: 'DELETE', name: i18n.t('enum.publish-operation.DELETE') },
    { value: 'MODIFY', name: i18n.t('enum.publish-operation.MODIFY') },
    { value: 'RESTORE', name: i18n.t('enum.publish-operation.RESTORE') },
];

export const ratkoPushErrorTypes: { value: RatkoPushErrorType; name: string }[] = [
    { value: 'PROPERTIES', name: i18n.t('enum.ratko-push-error-type.PROPERTIES') },
    { value: 'LOCATION', name: i18n.t('enum.ratko-push-error-type.LOCATION') },
    { value: 'GEOMETRY', name: i18n.t('enum.ratko-push-error-type.GEOMETRY') },
    { value: 'STATE', name: i18n.t('enum.ratko-push-error-type.STATE') },
];

export const ratkoPushErrorOperations: { value: RatkoPushErrorOperation; name: string }[] = [
    { value: 'CREATE', name: i18n.t('enum.ratko-push-error-operation.CREATE') },
    { value: 'UPDATE', name: i18n.t('enum.ratko-push-error-operation.UPDATE') },
    { value: 'DELETE', name: i18n.t('enum.ratko-push-error-operation.DELETE') },
];

export const topologicalConnectivityTypes: { value: TopologicalConnectivityType; name: string }[] =
    [
        { value: 'NONE', name: i18n.t('enum.topological-connectivity-type.NONE') },
        { value: 'START', name: i18n.t('enum.topological-connectivity-type.START') },
        { value: 'END', name: i18n.t('enum.topological-connectivity-type.END') },
        {
            value: 'START_AND_END',
            name: i18n.t('enum.topological-connectivity-type.START_AND_END'),
        },
    ];

export const planPhases: { value: PlanPhase; name: string }[] = [
    { value: 'RAILWAY_PLAN', name: i18n.t('enum.plan-phase.RAILWAY_PLAN') },
    {
        value: 'RAILWAY_CONSTRUCTION_PLAN',
        name: i18n.t('enum.plan-phase.RAILWAY_CONSTRUCTION_PLAN'),
    },
    { value: 'RENOVATION_PLAN', name: i18n.t('enum.plan-phase.RENOVATION_PLAN') },
    { value: 'ENHANCED_RENOVATION_PLAN', name: i18n.t('enum.plan-phase.ENHANCED_RENOVATION_PLAN') },
    { value: 'MAINTENANCE', name: i18n.t('enum.plan-phase.MAINTENANCE') },
    { value: 'NEW_INVESTMENT', name: i18n.t('enum.plan-phase.NEW_INVESTMENT') },
    { value: 'REMOVED_FROM_USE', name: i18n.t('enum.plan-phase.REMOVED_FROM_USE') },
];

export const planDecisionPhases: { value: DecisionPhase; name: string }[] = [
    { value: 'APPROVED_PLAN', name: i18n.t('enum.plan-decision.APPROVED_PLAN') },
    { value: 'UNDER_CONSTRUCTION', name: i18n.t('enum.plan-decision.UNDER_CONSTRUCTION') },
    { value: 'IN_USE', name: i18n.t('enum.plan-decision.IN_USE') },
];

export const measurementMethods: { value: MeasurementMethod; name: string }[] = [
    {
        value: 'VERIFIED_DESIGNED_GEOMETRY',
        name: i18n.t('enum.measurement-method.VERIFIED_DESIGNED_GEOMETRY'),
    },
    {
        value: 'OFFICIALLY_MEASURED_GEODETICALLY',
        name: i18n.t('enum.measurement-method.OFFICIALLY_MEASURED_GEODETICALLY'),
    },
    { value: 'TRACK_INSPECTION', name: i18n.t('enum.measurement-method.TRACK_INSPECTION') },
    {
        value: 'DIGITIZED_AERIAL_IMAGE',
        name: i18n.t('enum.measurement-method.DIGITIZED_AERIAL_IMAGE'),
    },
    {
        value: 'UNVERIFIED_DESIGNED_GEOMETRY',
        name: i18n.t('enum.measurement-method.UNVERIFIED_DESIGNED_GEOMETRY'),
    },
];

export const verticalCoordinateSystems: { value: VerticalCoordinateSystem; name: string }[] = [
    { value: 'N43', name: 'N43' },
    { value: 'N60', name: 'N60' },
    { value: 'N2000', name: 'N2000' },
];

export const switchTrapPoints: { value: TrapPoint; name: string }[] = [
    { value: TrapPoint.Yes, name: i18n.t('enum.trap-point.Yes') },
    { value: TrapPoint.No, name: i18n.t('enum.trap-point.No') },
    { value: TrapPoint.Unknown, name: i18n.t('enum.trap-point.Unknown') },
];

export function translateSwitchTrapPoint(trapPoint: TrapPoint): string {
    const translation = switchTrapPoints.find((option) => option.value == trapPoint)?.name;
    return translation || '';
}

export function switchJointNumberToString(joint: JointNumber): string {
    return joint.substring(6);
}
