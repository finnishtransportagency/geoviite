import { PreviewProps, PreviewView } from 'preview/preview-view';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import * as React from 'react';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';

export const PreviewContainer: React.FC = () => {
    const layoutContext = useTrackLayoutAppSelector((state) => state.layoutContext);
    const stagedPublicationCandidateReferences = useTrackLayoutAppSelector(
        (state) => state.previewState.stagedPublicationCandidateReferences,
    );
    const showOnlyOwnUnstagedChanges = useTrackLayoutAppSelector(
        (state) => state.previewState.showOnlyOwnUnstagedChanges,
    );
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);

    const props: PreviewProps = {
        layoutContext,
        changeTimes: changeTimes,
        stagedPublicationCandidateReferences,
        setStagedPublicationCandidateReferences: delegates.setStagedPublicationCandidateReferences,
        showOnlyOwnUnstagedChanges,
        setShowOnlyOwnUnstagedChanges: delegates.setShowOnlyOwnUnstagedChanges,
        onSelect: delegates.onSelect,
        onClosePreview: () => delegates.onLayoutModeChange('DEFAULT'),
        onPublish: delegates.onPublish,
        onShowOnMap: delegates.showArea,
    };

    return <PreviewView {...props} />;
};
