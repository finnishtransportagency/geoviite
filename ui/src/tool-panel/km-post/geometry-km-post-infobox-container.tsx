import * as React from 'react';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import {
    GeometryKmPostInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import GeometryKmPostInfobox from 'tool-panel/km-post/geometry-km-post-infobox';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { GeometryPlanId } from 'geometry/geometry-model';
import { createDelegates } from 'store/store-utils';
import { useCommonDataAppSelector } from 'store/hooks';

type GeometryKmPostInfoboxContainerProps = {
    kmPost: LayoutKmPost;
    planId: GeometryPlanId;
    visibility: GeometryKmPostInfoboxVisibilities;
    onVisiblityChange: (visibilites: GeometryKmPostInfoboxVisibilities) => void;
};

export const GeometryKmPostInfoboxContainer: React.FC<GeometryKmPostInfoboxContainerProps> = ({
    kmPost,
    planId,
    visibility,
    onVisiblityChange,
}) => {
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    return (
        <GeometryKmPostInfobox
            geometryKmPost={kmPost}
            planId={planId}
            onShowOnMap={() =>
                kmPost.layoutLocation &&
                delegates.showArea(calculateBoundingBoxToShowAroundLocation(kmPost.layoutLocation))
            }
            visibilities={visibility}
            onVisibilityChange={onVisiblityChange}
            changeTimes={changeTimes}
        />
    );
};
