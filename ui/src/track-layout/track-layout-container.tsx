import * as React from 'react';
import { TrackLayoutView } from 'track-layout/track-layout-view';
import { useTrackLayoutAppSelector } from 'store/hooks';

export const TrackLayoutContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const showVerticalGeometryDiagram =
        trackLayoutState.map.verticalGeometryDiagramState.visible &&
        (trackLayoutState.selectedToolPanelTab?.type === 'GEOMETRY_ALIGNMENT' ||
            trackLayoutState.selectedToolPanelTab?.type === 'LOCATION_TRACK');

    return <TrackLayoutView showVerticalGeometryDiagram={showVerticalGeometryDiagram} />;
};
