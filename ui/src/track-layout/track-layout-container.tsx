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
                type: id,
                visible: visible,
            }),
        onReferenceLineVisibilityChange: (id: string, visible: boolean) =>
            delegates.onReferencelineVisibilityChange({
                type: id,
                visible: visible,
            }),
        onMissingVerticalGeometryVisibilityChange: (id: string, visible: boolean) =>
            delegates.onMissingVerticalGeometryVisibilityChange({
                type: id,
                visible: visible,
            }),
        onSegmentsFromSelectedPlanVisibilityChange: (id: string, visible: boolean) =>
            delegates.onShowSegmentsFromSelectedPlanVisibilityChange({
                type: id,
                visible: visible,
            }),
        onMissingLinkingVisibilityChange: (id: string, visible: boolean) =>
            delegates.onMissingLinkingVisibilityChange({
                type: id,
                visible: visible,
            }),
        onDuplicateTracksVisibilityChange: (id: string, visible: boolean) =>
            delegates.onDuplicateTracksVisibilityChange({
                type: id,
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
