import * as React from 'react';
import KmPostInfobox from 'tool-panel/km-post/km-post-infobox';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import {
    KmPostInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { LayoutKmPost } from 'track-layout/track-layout-model';

type KmPostInfoboxContainerProps = {
    kmPost: LayoutKmPost;
    visibilities: KmPostInfoboxVisibilities;
    onVisibilityChange: (visibilities: KmPostInfoboxVisibilities) => void;
    onDataChange: () => void;
};

export const KmPostInfoboxContainer: React.FC<KmPostInfoboxContainerProps> = ({
    kmPost,
    visibilities,
    onDataChange,
    onVisibilityChange,
}) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);

    return (
        <KmPostInfobox
            kmPost={kmPost}
            changeTimes={changeTimes}
            visibilities={visibilities}
            onVisibilityChange={onVisibilityChange}
            layoutContext={trackLayoutState.layoutContext}
            kmPostChangeTime={changeTimes.layoutKmPost}
            onDataChange={onDataChange}
            onSelect={delegates.onSelect}
            onUnselect={delegates.onUnselect}
            onShowOnMap={() =>
                kmPost.layoutLocation &&
                delegates.showArea(calculateBoundingBoxToShowAroundLocation(kmPost.layoutLocation))
            }
        />
    );
};
