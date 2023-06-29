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
            onSelect={delegates.onSelect}
            onPublishTypeChange={delegates.onPublishTypeChange}
            onOpenPreview={() => delegates.onLayoutModeChange('PREVIEW')}
            showArea={delegates.showArea}
            onLayerMenuItemChange={delegates.onLayerMenuItemChange}
            changeTimes={changeTimes}
            onStopLinking={delegates.stopLinking}
            linkingState={trackLayoutState.linkingState}
            selectedToolPanelTab={trackLayoutState.selectedToolPanelTab}
        />
    );
};
