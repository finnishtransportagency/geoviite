import * as React from 'react';
import { TrackLayoutView } from 'track-layout/track-layout-view';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { getLayoutDesignOrUndefined } from 'track-layout/layout-design-api';
import { getChangeTimes } from 'common/change-time-api';

export const TrackLayoutContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const showVerticalGeometryDiagram =
        trackLayoutState.map.verticalGeometryDiagramState.visible &&
        (trackLayoutState.selectedToolPanelTab?.type === 'GEOMETRY_ALIGNMENT' ||
            trackLayoutState.selectedToolPanelTab?.type === 'LOCATION_TRACK');

    const designId =
        trackLayoutState.layoutContextMode === 'DESIGN' ? trackLayoutState.designId : undefined;
    const [currentDesign, designLoadStatus] = useLoaderWithStatus(
        () => designId && getLayoutDesignOrUndefined(getChangeTimes().layoutDesign, designId),
        [getChangeTimes().layoutDesign, designId],
    );
    const currentDesignExists =
        designLoadStatus === LoaderStatus.Ready && currentDesign !== undefined;
    const isEnabled = trackLayoutState.layoutContextMode !== 'DESIGN' || currentDesignExists;

    return (
        <TrackLayoutView
            showVerticalGeometryDiagram={showVerticalGeometryDiagram}
            enabled={isEnabled}
        />
    );
};
