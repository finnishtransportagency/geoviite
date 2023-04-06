import * as React from 'react';
import { TrackLayoutView } from 'track-layout/track-layout-view';
import { actionCreators } from './track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { useAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';

export const TrackLayoutContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const dispatch = useAppDispatch();
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
        onReferenceLineVisibilityChange: (id: string, visible: boolean) =>
            delegates.onReferencelineVisibilityChange({
                layerId: id,
                visible: visible,
            }),
        onMissingLinkingVisibilityChange: (id: string, visible: boolean) =>
            delegates.onMissingLinkingVisibilityChange({
                layerId: id,
                visible: visible,
            }),
        onDuplicateTracksVisibilityChange: (id: string, visible: boolean) =>
            delegates.onDuplicateTracksVisibilityChange({
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
