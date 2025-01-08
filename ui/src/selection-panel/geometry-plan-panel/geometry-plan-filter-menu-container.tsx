import * as React from 'react';
import { GeometryPlanFilterMenu } from 'selection-panel/geometry-plan-panel/geometry-plan-filter-menu';
import { useTrackLayoutAppSelector } from 'store/hooks';

type GeometryPlanFilterMenuContainerProps = {
    some?: string;
};

export const GeometryPlanFilterMenuContainer: React.FC<GeometryPlanFilterMenuContainerProps> = (
    _,
) => {
    const state = useTrackLayoutAppSelector((state) => state);
    return <GeometryPlanFilterMenu grouping={state.geometryPlanViewSettings.grouping} />;
};
