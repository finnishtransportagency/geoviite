import * as React from 'react';
import { TrackLayoutView } from 'track-layout/track-layout-view';
import { trackLayoutActionCreators } from './track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';

export const TrackLayoutContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = createDelegates(trackLayoutActionCreators);

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
        onMissingVerticalGeometryVisibilityChange: (id: string, visible: boolean) =>
            delegates.onMissingVerticalGeometryVisibilityChange({
                layerId: id,
                visible: visible,
            }),
        onSegmentsFromSelectedPlanVisibilityChange: (id: string, visible: boolean) =>
            delegates.onShowSegmentsFromSelectedPlanVisibilityChange({
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
        changeTimes: changeTimes,
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
