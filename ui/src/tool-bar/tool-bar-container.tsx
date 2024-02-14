import * as React from 'react';
import { ToolBar } from 'tool-bar/tool-bar';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';

export const ToolBarContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);

    return (
        <ToolBar
            publishType={trackLayoutState.publishType}
            showArea={delegates.showArea}
            onSelect={delegates.onSelect}
            onUnselect={delegates.onUnselect}
            onPublishTypeChange={delegates.onPublishTypeChange}
            onOpenPreview={() => delegates.onLayoutModeChange('PREVIEW')}
            changeTimes={changeTimes}
            onStopLinking={delegates.stopLinking}
            linkingState={trackLayoutState.linkingState}
            splittingState={trackLayoutState.splittingState}
            visibleLayers={trackLayoutState.map.visibleLayers}
            mapLayerMenuGroups={trackLayoutState.map.layerMenu}
            onMapLayerChange={delegates.onLayerMenuItemChange}
        />
    );
};
