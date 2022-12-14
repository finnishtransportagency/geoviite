import * as React from 'react';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
import GeometryKmPostInfoboxView from 'tool-panel/km-post/geometry-km-post-infobox-view';
import { LinkingType } from 'linking/linking-model';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { SelectedGeometryItem } from 'selection/selection-model';
import { BoundingBox } from 'model/geometry';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';

type GeometryKmPostInfoboxContainerProps = {
    geometryKmPost: SelectedGeometryItem<LayoutKmPost>;
    showArea: (bbox: BoundingBox) => void;
};

const GeometryKmPostInfoboxContainer: React.FC<GeometryKmPostInfoboxContainerProps> = ({
    geometryKmPost,
    showArea,
}) => {
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);
    const state = useTrackLayoutAppSelector((state) => state.trackLayout);
    const selectedLayoutKmPost = state.selection.selectedItems.kmPosts[0];
    const kmPostChangeTime = state.changeTimes.layoutKmPost;

    return (
        <GeometryKmPostInfoboxView
            geometryKmPost={geometryKmPost.geometryItem}
            planId={geometryKmPost.planId}
            layoutKmPost={selectedLayoutKmPost}
            kmPostChangeTime={kmPostChangeTime}
            linkingState={
                state.linkingState?.type == LinkingType.LinkingKmPost
                    ? state.linkingState
                    : undefined
            }
            startLinking={delegates.startKmPostLinking}
            stopLinking={delegates.stopLinking}
            onKmPostSelect={(kmPost: LayoutKmPost) => delegates.onSelect({ kmPosts: [kmPost] })}
            publishType={state.publishType}
            onShowOnMap={() =>
                geometryKmPost.geometryItem.location &&
                showArea(
                    calculateBoundingBoxToShowAroundLocation(geometryKmPost.geometryItem.location),
                )
            }
        />
    );
};

export default GeometryKmPostInfoboxContainer;
