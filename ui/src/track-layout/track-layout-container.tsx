import * as React from 'react';
import { TrackLayoutView } from 'track-layout/track-layout-view';
import { trackLayoutActionCreators } from './track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';

export const TrackLayoutContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);

    const showVerticalGeometryDiagram =
        trackLayoutState.map.verticalGeometryDiagramState.visible &&
        (trackLayoutState.selectedToolPanelTab?.type === 'GEOMETRY_ALIGNMENT' ||
            trackLayoutState.selectedToolPanelTab?.type === 'LOCATION_TRACK');

    return (
        <TrackLayoutView
            publishType={trackLayoutState.publishType}
            mapLayerMenuGroups={trackLayoutState.map.layerMenu}
            onSelect={delegates.onSelect}
            onPublishTypeChange={delegates.onPublishTypeChange}
            onLayoutModeChange={delegates.onLayoutModeChange}
            showArea={delegates.showArea}
            onLayerMenuItemChange={delegates.onLayerMenuItemChange}
            changeTimes={changeTimes}
            onStopLinking={delegates.stopLinking}
            linkingState={trackLayoutState.linkingState}
            showVerticalGeometryDiagram={showVerticalGeometryDiagram}
        />
    );
};
