import * as React from 'react';
import GeometrySwitchInfobox from 'tool-panel/switch/geometry-switch-infobox';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import {
    GeometrySwitchInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { GeometryPlanId, GeometrySwitchId } from 'geometry/geometry-model';

type GeometrySwitchInfoboxContainerProps = {
    onVisibilityChange: (visibilities: GeometrySwitchInfoboxVisibilities) => void;
    visibilities: GeometrySwitchInfoboxVisibilities;
    switchId: GeometrySwitchId;
    layoutSwitch: LayoutSwitch | undefined;
    planId: GeometryPlanId;
};

export const GeometrySwitchInfoboxContainer: React.FC<GeometrySwitchInfoboxContainerProps> = ({
    switchId,
    layoutSwitch,
    planId,
    visibilities,
    onVisibilityChange,
}) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);

    const onShowMapLocation = (location: Point) =>
        delegates.showArea(calculateBoundingBoxToShowAroundLocation(location));

    return (
        <GeometrySwitchInfobox
            visibilities={visibilities}
            onVisibilityChange={onVisibilityChange}
            switchId={switchId}
            layoutSwitch={layoutSwitch}
            suggestedSwitch={undefined}
            linkingState={trackLayoutState.linkingState}
            planId={planId}
            switchChangeTime={changeTimes.layoutSwitch}
            locationTrackChangeTime={changeTimes.layoutLocationTrack}
            onShowOnMap={onShowMapLocation}
        />
    );
};
