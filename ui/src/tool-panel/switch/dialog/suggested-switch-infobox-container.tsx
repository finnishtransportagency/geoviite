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
import { SuggestedSwitch } from 'linking/linking-model';

type SuggestedSwitchInfoboxContainerProps = {
    onVisibilityChange: (visibilities: GeometrySwitchInfoboxVisibilities) => void;
    visibilities: GeometrySwitchInfoboxVisibilities;
    suggestedSwitch: SuggestedSwitch;
    layoutSwitch: LayoutSwitch | undefined;
};

export const SuggestedSwitchInfoboxContainer: React.FC<SuggestedSwitchInfoboxContainerProps> = ({
    suggestedSwitch,
    layoutSwitch,
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
            switchId={suggestedSwitch.id}
            layoutSwitch={layoutSwitch}
            suggestedSwitch={suggestedSwitch}
            linkingState={trackLayoutState.linkingState}
            planId={undefined}
            changeTimes={changeTimes}
            locationTrackChangeTime={changeTimes.layoutLocationTrack}
            onShowOnMap={onShowMapLocation}
        />
    );
};
