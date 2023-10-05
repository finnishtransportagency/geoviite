import {
    GeometryElement,
    GeometryElementId,
    GeometryPlan,
    GeometrySwitch,
    GeometrySwitchId,
} from 'geometry/geometry-model';

export function getGeometryElementFromPlan(
    plan: GeometryPlan,
    geomElementId: GeometryElementId,
): GeometryElement | undefined {
    return (
        plan.alignments
            .flatMap((a) => a.elements)
            .find((element) => element.id === geomElementId) || undefined
    );
}

export function getGeometrySwitchFromPlan(
    plan: GeometryPlan,
    geomSwitchId: GeometrySwitchId,
): GeometrySwitch | undefined {
    return plan.switches.find((geomSwitch) => geomSwitch.id === geomSwitchId) || undefined;
}
