import * as React from 'react';
import GeometrySwitchLinkingInfobox from 'tool-panel/switch/geometry-switch-linking-infobox';
import { LinkingSwitch, SuggestedSwitch } from 'linking/linking-model';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'store/track-layout-store';
import { TimeStamp } from 'common/common-model';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import { GeometryPlanId, GeometrySwitchId } from 'geometry/geometry-model';

type GeometrySwitchLinkingContainerProps = {
    linkingState?: LinkingSwitch;
    switchId?: GeometrySwitchId;
    suggestedSwitch?: SuggestedSwitch;
    switchChangeTime: TimeStamp;
    locationTrackChangeTime: TimeStamp;
    layoutSwitch?: LayoutSwitch;
    planId?: GeometryPlanId;
};

const GeometrySwitchLinkingContainer: React.FC<GeometrySwitchLinkingContainerProps> = ({
    linkingState,
    switchId,
    suggestedSwitch,
    switchChangeTime,
    locationTrackChangeTime,
    layoutSwitch,
    planId,
}) => {
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);
    const store = useTrackLayoutAppSelector((state) => state.trackLayout);

    return (
        <GeometrySwitchLinkingInfobox
            linkingState={linkingState}
            switchId={switchId}
            onLinkingStart={delegates.startSwitchLinking}
            selectedSuggestedSwitch={suggestedSwitch}
            onSwitchSelect={(s) => delegates.onSelect({ switches: [s.id] })}
            switchChangeTime={switchChangeTime}
            locationTrackChangeTime={locationTrackChangeTime}
            layoutSwitch={layoutSwitch}
            publishType={store.publishType}
            resolution={store.map.viewport.resolution}
            onStopLinking={delegates.stopLinking}
            onSuggestedSwitchChange={(s) => delegates.onSelect({ suggestedSwitches: [s] })}
            planId={planId}
            geometrySwitchId={switchId}
        />
    );
};

export default GeometrySwitchLinkingContainer;
