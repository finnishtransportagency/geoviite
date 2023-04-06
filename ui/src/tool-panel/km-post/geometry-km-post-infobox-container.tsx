import * as React from 'react';
import { useAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import GeometryKmPostInfoboxView from 'tool-panel/km-post/geometry-km-post-infobox-view';
import { LinkingType } from 'linking/linking-model';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { SelectedGeometryItem } from 'selection/selection-model';
import { BoundingBox } from 'model/geometry';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { useKmPost } from 'track-layout/track-layout-react-utils';

type GeometryKmPostInfoboxContainerProps = {
    geometryKmPost: SelectedGeometryItem<LayoutKmPost>;
    showArea: (bbox: BoundingBox) => void;
};

const GeometryKmPostInfoboxContainer: React.FC<GeometryKmPostInfoboxContainerProps> = ({
    geometryKmPost,
    showArea,
}) => {
    const dispatch = useAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);
    const state = useTrackLayoutAppSelector((state) => state);
    const kmPostChangeTime = state.changeTimes.layoutKmPost;
    const selectedLayoutKmPost = useKmPost(
        state.selection.selectedItems.kmPosts[0],
        state.publishType,
        kmPostChangeTime,
    );

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
            onKmPostSelect={(kmPost: LayoutKmPost) => delegates.onSelect({ kmPosts: [kmPost.id] })}
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
