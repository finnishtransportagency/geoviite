import * as React from 'react';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { useKmPost } from 'track-layout/track-layout-react-utils';
import GeometryKmPostLinkingInfobox from 'tool-panel/km-post/geometry-km-post-linking-infobox';
import { GeometryPlanId } from 'geometry/geometry-model';
import { LinkingType } from 'linking/linking-model';
import { MapLayerName } from 'map/map-model';
import { first } from 'utils/array-utils';

type GeometryKmPostLinkingContainerProps = {
    geometryKmPost: LayoutKmPost;
    planId: GeometryPlanId;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
};

const GeometryKmPostLinkingContainer: React.FC<GeometryKmPostLinkingContainerProps> = ({
    geometryKmPost,
    contentVisible,
    onContentVisibilityChange,
    planId,
}) => {
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const state = useTrackLayoutAppSelector((state) => state);
    const kmPostChangeTime = useCommonDataAppSelector((state) => state.changeTimes.layoutKmPost);
    const selectedLayoutKmPost = useKmPost(
        first(state.selection.selectedItems.kmPosts),
        state.layoutContext,
        kmPostChangeTime,
    );

    const linkingLayers: MapLayerName[] = [
        'km-post-layer',
        'virtual-km-post-linking-layer',
        'reference-line-alignment-layer',
        'reference-line-badge-layer',
    ];

    return (
        <GeometryKmPostLinkingInfobox
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}
            geometryKmPost={geometryKmPost}
            planId={planId}
            layoutKmPost={selectedLayoutKmPost}
            kmPostChangeTime={kmPostChangeTime}
            linkingState={
                state.linkingState?.type === LinkingType.LinkingKmPost
                    ? state.linkingState
                    : undefined
            }
            startLinking={(id) => {
                delegates.addForcedVisibleLayer(linkingLayers);
                delegates.startKmPostLinking(id);
            }}
            stopLinking={() => {
                delegates.removeForcedVisibleLayer(linkingLayers);
                delegates.stopLinking();
            }}
            onSelect={delegates.onSelect}
            onUnselect={delegates.onUnselect}
            layoutContext={state.layoutContext}
        />
    );
};

export default GeometryKmPostLinkingContainer;
