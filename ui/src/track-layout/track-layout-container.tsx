import * as React from 'react';
import { TrackLayoutView } from 'track-layout/track-layout-view';
import { trackLayoutActionCreators } from './track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';

export const TrackLayoutContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = createDelegates(trackLayoutActionCreators);

    return (
        <TrackLayoutView
            publishType={trackLayoutState.publishType}
            map={trackLayoutState.map}
            selection={trackLayoutState.selection}
            onViewportChange={delegates.onViewportChange}
            onSelect={delegates.onSelect}
            onHighlightItems={delegates.onHighlightItems}
            onHoverLocation={delegates.onHoverLocation}
            onClickLocation={delegates.onClickLocation}
            onPublishTypeChange={delegates.onPublishTypeChange}
            onOpenPreview={() => delegates.onLayoutModeChange('PREVIEW')}
            onShownItemsChange={delegates.onShownItemsChange}
            showArea={delegates.showArea}
            onSetLayoutClusterLinkPoint={delegates.setLayoutClusterLinkPoint}
            onSetGeometryClusterLinkPoint={delegates.setGeometryClusterLinkPoint}
            onRemoveGeometryLinkPoint={delegates.removeGeometryLinkPoint}
            onRemoveLayoutLinkPoint={delegates.removeLayoutLinkPoint}
            onLayerSettingsChange={delegates.onSettingsChange}
            changeTimes={changeTimes}
            onStopLinking={delegates.stopLinking}
            linkingState={trackLayoutState.linkingState}
        />
    );
};
