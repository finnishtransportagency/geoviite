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
): GeometryElement | null {
    return (
        plan.alignments
            .flatMap((a) => a.elements)
            .find((element) => element.id === geomElementId) || null
    );
}

export function getGeometrySwitchFromPlan(
    plan: GeometryPlan,
    geomSwitchId: GeometrySwitchId,
): GeometrySwitch | null {
    return plan.switches.find((geomSwitch) => geomSwitch.id === geomSwitchId) || null;
}
