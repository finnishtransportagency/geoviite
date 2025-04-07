import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { AlignmentId, LocationTrackId } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';

export type PlanAlignmentKey = `${GeometryPlanId}_${GeometryAlignmentId}`;

export type VisibleExtentLookup = {
    plan: { [k in PlanAlignmentKey]?: [number, number] };
    layout: { [k in AlignmentId]?: [number, number] };
};

export const planAlignmentKey = (
    geometryPlanId: GeometryPlanId,
    geometryAlignmentId: GeometryAlignmentId,
): PlanAlignmentKey => `${geometryPlanId}_${geometryAlignmentId}`;

export type VerticalGeometryDiagramState = {
    visibleExtentLookup: VisibleExtentLookup;
    visible: boolean;
};

export const initialVerticalGeometryDiagramState: VerticalGeometryDiagramState = {
    visibleExtentLookup: { plan: {}, layout: {} },
    visible: false,
};

export type VerticalGeometryDiagramGeometryAlignmentId = {
    planId: GeometryPlanId;
    alignmentId: GeometryAlignmentId;
};
export type VerticalGeometryDiagramLayoutAlignmentId = {
    locationTrackId: LocationTrackId;
    layoutContext: LayoutContext;
};

export type VerticalGeometryDiagramAlignmentId =
    | VerticalGeometryDiagramGeometryAlignmentId
    | VerticalGeometryDiagramLayoutAlignmentId;
