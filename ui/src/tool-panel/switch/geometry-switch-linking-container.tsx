import * as React from 'react';
import GeometrySwitchLinkingInfobox from 'tool-panel/switch/geometry-switch-linking-infobox';
import { LinkingState, SuggestedSwitch } from 'linking/linking-model';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import {
    SwitchLinkingInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { TimeStamp } from 'common/common-model';
import { GeometryPlanId, GeometrySwitch } from 'geometry/geometry-model';
import { LayoutSwitchId } from 'track-layout/track-layout-model';

type GeometrySwitchLinkingContainerProps = {
    linkingState: LinkingState | undefined;
    geometrySwitch: GeometrySwitch;
    switchChangeTime: TimeStamp;
    locationTrackChangeTime: TimeStamp;
    geometryPlanId: GeometryPlanId;
    visibilities: SwitchLinkingInfoboxVisibilities;
    onVisibilityChange: (visibilities: SwitchLinkingInfoboxVisibilities) => void;
};

const GeometrySwitchLinkingContainer: React.FC<GeometrySwitchLinkingContainerProps> = ({
    linkingState,
    geometrySwitch,
    switchChangeTime,
    locationTrackChangeTime,
    geometryPlanId,
    visibilities,
    onVisibilityChange,
}) => {
    const layoutContext = useTrackLayoutAppSelector((state) => state.layoutContext);
    const resolution = useTrackLayoutAppSelector((state) => state.map.viewport.resolution);

    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const onLinkingStart = React.useCallback((suggestedSwitch: SuggestedSwitch) => {
        delegates.addForcedVisibleLayer(['switch-linking-layer']);
        delegates.startGeometrySwitchLinking({
            suggestedSwitch,
            geometrySwitch,
            geometryPlanId,
        });
    }, []);

    const onStopLinking = React.useCallback(() => {
        delegates.removeForcedVisibleLayer(['switch-linking-layer']);
        delegates.stopLinking();
    }, []);

    const selectCandidateSwitchForLinking = React.useCallback(
        (suggestedSwitch: SuggestedSwitch, layoutSwitchId: LayoutSwitchId) =>
            delegates.selectCandidateSwitchForGeometrySwitchLinking({
                suggestedSwitch,
                layoutSwitchId,
            }),
        [],
    );

    return (
        <GeometrySwitchLinkingInfobox
            geometrySwitch={geometrySwitch}
            visibilities={visibilities}
            onVisibilityChange={onVisibilityChange}
            linkingState={linkingState}
            onLinkingStart={onLinkingStart}
            selectCandidateSwitchForLinking={selectCandidateSwitchForLinking}
            onSelect={delegates.onSelect}
            onUnselect={delegates.onUnselect}
            switchChangeTime={switchChangeTime}
            locationTrackChangeTime={locationTrackChangeTime}
            layoutContext={layoutContext}
            resolution={resolution}
            onStopLinking={onStopLinking}
            planId={geometryPlanId}
        />
    );
};

export default GeometrySwitchLinkingContainer;
