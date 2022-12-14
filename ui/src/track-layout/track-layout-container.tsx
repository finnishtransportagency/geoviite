import * as React from 'react';
import { TrackLayoutView } from 'track-layout/track-layout-view';
import { actionCreators } from './track-layout-store';
import { createDelegates } from 'store/store-utils';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';

export const TrackLayoutContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state.trackLayout);
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, actionCreators);

    const props = {
        ...trackLayoutState,
        onViewportChange: delegates.onViewportChange,
        onSelect: delegates.onSelect,
        onHighlightItems: delegates.onHighlightItems,
        onHoverLocation: delegates.onHoverLocation,
        onClickLocation: delegates.onClickLocation,
        onMapSettingsVisibilityChange: delegates.onMapSettingsVisibilityChange,
        onPublishTypeChange: delegates.onPublishTypeChange,
        onOpenPreview: () => delegates.onLayoutModeChange('PREVIEW'),
        onLayerVisibilityChange: (id: string, visible: boolean) =>
            delegates.onLayerVisibilityChange({
                layerId: id,
                visible: visible,
            }),
        onTrackNumberVisibilityChange: (id: string, visible: boolean) =>
            delegates.onTrackNumberVisibilityChange({
                layerId: id,
                visible: visible,
            }),
        changeTimes: trackLayoutState.changeTimes,
        onShownItemsChange: delegates.onShownItemsChange,
        showArea: delegates.showArea,
        onSetLayoutClusterLinkPoint: delegates.setLayoutClusterLinkPoint,
        onSetGeometryClusterLinkPoint: delegates.setGeometryClusterLinkPoint,
        onRemoveGeometryLinkPoint: delegates.removeGeometryLinkPoint,
        onRemoveLayoutLinkPoint: delegates.removeLayoutLinkPoint,
        onStopLinking: delegates.stopLinking,
    };

    return <TrackLayoutView {...props} />;
};
