import { PreviewView } from 'preview/preview-view';
import { actionCreators } from 'track-layout/track-layout-store';
import { createDelegates } from 'store/store-utils';
import * as React from 'react';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';

export const PreviewContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state.trackLayout);
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, actionCreators);

    const props = {
        map: trackLayoutState.map,
        selection: trackLayoutState.selection,
        changeTimes: trackLayoutState.changeTimes,
        onViewportChange: delegates.onViewportChange,
        onSelect: delegates.onSelect,
        onHighlightItems: delegates.onHighlightItems,
        onHoverLocation: delegates.onHoverLocation,
        onShownItemsChange: delegates.onShownItemsChange,
        onClosePreview: () => delegates.onLayoutModeChange('DEFAULT'),
    };

    return <PreviewView {...props} />;
};
