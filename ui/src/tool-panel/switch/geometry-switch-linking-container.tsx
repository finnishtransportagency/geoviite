import * as React from 'react';
import GeometrySwitchLinkingInfobox from 'tool-panel/switch/geometry-switch-linking-infobox';
import { LinkingSwitch, SuggestedSwitch } from 'linking/linking-model';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import {
    GeometrySwitchLinkingInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
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
    visibilities: GeometrySwitchLinkingInfoboxVisibilities;
    onVisibilityChange: (visibilities: GeometrySwitchLinkingInfoboxVisibilities) => void;
};

const GeometrySwitchLinkingContainer: React.FC<GeometrySwitchLinkingContainerProps> = ({
    linkingState,
    switchId,
    suggestedSwitch,
    switchChangeTime,
    locationTrackChangeTime,
    layoutSwitch,
    planId,
    visibilities,
    onVisibilityChange,
}) => {
    const delegates = createDelegates(TrackLayoutActions);
    const store = useTrackLayoutAppSelector((state) => state);

    return (
        <GeometrySwitchLinkingInfobox
            visibilities={visibilities}
            onVisibilityChange={onVisibilityChange}
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
