import { PreviewView } from 'preview/preview-view';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import * as React from 'react';
import { useAppDispatch, useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';

export const PreviewContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const dispatch = useAppDispatch();
    const delegates = createDelegates(dispatch, trackLayoutActionCreators);

    const props = {
        map: trackLayoutState.map,
        selection: trackLayoutState.selection,
        changeTimes: changeTimes,
        selectedPublishCandidateIds: trackLayoutState.stagedPublicationRequestIds,
        onViewportChange: delegates.onViewportChange,
        onSelect: delegates.onSelect,
        onHighlightItems: delegates.onHighlightItems,
        onHoverLocation: delegates.onHoverLocation,
        onClickLocation: delegates.onClickLocation,
        onShownItemsChange: delegates.onShownItemsChange,
        onClosePreview: () => delegates.onLayoutModeChange('DEFAULT'),
        onPublish: delegates.onPublish,
        onPreviewSelect: delegates.onPreviewSelect,
        onPublishPreviewRemove: delegates.onPublishPreviewRemove,
    };

    return <PreviewView {...props} />;
};
