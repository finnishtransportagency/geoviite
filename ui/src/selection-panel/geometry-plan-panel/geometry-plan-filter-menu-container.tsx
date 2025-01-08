import * as React from 'react';
import { GeometryPlanFilterMenu } from 'selection-panel/geometry-plan-panel/geometry-plan-filter-menu';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';

type GeometryPlanFilterMenuContainerProps = {
    some?: string;
};

export const GeometryPlanFilterMenuContainer: React.FC<GeometryPlanFilterMenuContainerProps> = (
    _,
) => {
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);
    const state = useTrackLayoutAppSelector((state) => state);

    return (
        <GeometryPlanFilterMenu
            grouping={state.geometryPlanViewSettings.grouping}
            onGroupingChanged={delegates.updateGeometryPlanGrouping}
            visibleSources={state.geometryPlanViewSettings.visibleSources}
            onVisibleSourcesChanged={delegates.updateGeometryPlanVisibleSources}
        />
    );
};
