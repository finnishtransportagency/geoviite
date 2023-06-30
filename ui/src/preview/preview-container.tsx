import { PreviewProps, PreviewView } from 'preview/preview-view';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import * as React from 'react';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';

export const PreviewContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);

    const props: PreviewProps = {
        changeTimes: changeTimes,
        selectedPublishCandidateIds: trackLayoutState.stagedPublicationRequestIds,
        onSelect: delegates.onSelect,
        onClosePreview: () => delegates.onLayoutModeChange('DEFAULT'),
        onPublish: delegates.onPublish,
        onPreviewSelect: delegates.onPreviewSelect,
        onPublishPreviewRemove: delegates.onPublishPreviewRemove,
        onShowOnMap: delegates.showArea,
    };

    return <PreviewView {...props} />;
};
