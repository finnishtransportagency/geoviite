import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { AlignmentId, LocationTrackId } from 'track-layout/track-layout-model';
import { PublishType } from 'common/common-model';

type PlanAlignmentKey = `${GeometryPlanId}_${GeometryAlignmentId}`;

export const planAlignmentKey = (
    geometryPlanId: GeometryPlanId,
    geometryAlignmentId: GeometryAlignmentId,
): PlanAlignmentKey => `${geometryPlanId}_${geometryAlignmentId}`;

export type VerticalGeometryDiagramState = {
    planAlignmentVisibleExtent: { [k in PlanAlignmentKey]: [number, number] };
    layoutAlignmentVisibleExtent: { [k in AlignmentId]: [number, number] };
    visible: boolean;
};

export const initialVerticalGeometryDiagramState: VerticalGeometryDiagramState = {
    planAlignmentVisibleExtent: {},
    layoutAlignmentVisibleExtent: {},
    visible: false,
};

export type VerticalGeometryDiagramAlignmentId =
    | { planId: GeometryPlanId; alignmentId: GeometryAlignmentId }
    | { locationTrackId: LocationTrackId; publishType: PublishType };
