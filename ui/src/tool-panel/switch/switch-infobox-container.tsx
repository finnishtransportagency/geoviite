import * as React from 'react';
import { LinkingType } from 'linking/linking-model';
import SwitchInfobox from 'tool-panel/switch/switch-infobox';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import {
    SwitchInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { LayoutSwitchId, LocationTrackId } from 'track-layout/track-layout-model';

type SwitchInfoboxContainerProps = {
    switchId: LayoutSwitchId;
    onVisibilityChange: (visibilities: SwitchInfoboxVisibilities) => void;
    visibilities: SwitchInfoboxVisibilities;
    onDataChange: () => void;
};

export const SwitchInfoboxContainer: React.FC<SwitchInfoboxContainerProps> = ({
    visibilities,
    onVisibilityChange,
    switchId,
    onDataChange,
}) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);

    return (
        <SwitchInfobox
            visibilities={visibilities}
            onVisibilityChange={onVisibilityChange}
            switchId={switchId}
            showArea={delegates.showArea}
            layoutContext={trackLayoutState.layoutContext}
            changeTimes={changeTimes}
            onDataChange={onDataChange}
            onSelect={delegates.onSelect}
            onUnselect={delegates.onUnselect}
            placingSwitchLinkingState={
                trackLayoutState.linkingState?.type === LinkingType.PlacingLayoutSwitch
                    ? trackLayoutState.linkingState
                    : undefined
            }
            startSwitchPlacing={delegates.startSwitchPlacing}
            stopLinking={() => {
                delegates.hideLayers(['switch-linking-layer']);
                delegates.stopLinking();
            }}
            onSelectLocationTrackBadge={(locationTrackId: LocationTrackId) => {
                delegates.onSelect({
                    locationTracks: [locationTrackId],
                });

                delegates.setToolPanelTab({
                    id: locationTrackId,
                    type: 'LOCATION_TRACK',
                });
            }}
        />
    );
};
