import * as React from 'react';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { useKmPost } from 'track-layout/track-layout-react-utils';
import GeometryKmPostLinkingInfobox from 'tool-panel/km-post/geometry-km-post-linking-infobox';
import { GeometryPlanId } from 'geometry/geometry-model';
import { LinkingType } from 'linking/linking-model';

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
    const delegates = createDelegates(TrackLayoutActions);
    const state = useTrackLayoutAppSelector((state) => state);
    const kmPostChangeTime = useCommonDataAppSelector((state) => state.changeTimes.layoutKmPost);
    const selectedLayoutKmPost = useKmPost(
        state.selection.selectedItems.kmPosts[0],
        state.publishType,
        kmPostChangeTime,
    );

    return (
        <GeometryKmPostLinkingInfobox
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}
            geometryKmPost={geometryKmPost}
            planId={planId}
            layoutKmPost={selectedLayoutKmPost}
            kmPostChangeTime={kmPostChangeTime}
            linkingState={
                state.linkingState?.type == LinkingType.LinkingKmPost
                    ? state.linkingState
                    : undefined
            }
            startLinking={delegates.startKmPostLinking}
            stopLinking={delegates.stopLinking}
            onKmPostSelect={(kmPost: LayoutKmPost) => delegates.onSelect({ kmPosts: [kmPost.id] })}
            publishType={state.publishType}
        />
    );
};

export default GeometryKmPostLinkingContainer;
