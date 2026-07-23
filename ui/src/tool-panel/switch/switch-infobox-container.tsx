import * as React from 'react';
import { LinkingType } from 'linking/linking-model';
import SwitchInfobox from 'tool-panel/switch/switch-infobox';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import {
    GeometrySwitchInfoboxVisibilities,
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
            switchLinkingVisibilities={trackLayoutState.infoboxVisibilities.geometrySwitch}
            onSwitchLinkingVisibilityChange={(visibilities: GeometrySwitchInfoboxVisibilities) =>
                delegates.onInfoboxVisibilityChange({
                    ...trackLayoutState.infoboxVisibilities,
                    geometrySwitch: visibilities,
                })
            }
            placingSwitchLinkingState={
                trackLayoutState.linkingState?.type === LinkingType.PlacingLayoutSwitch
                    ? trackLayoutState.linkingState
                    : undefined
            }
            startSwitchPlacing={(layoutSwitch) => {
                // Force the switch-linking-layer visible so the live hover preview can render while
                // placing; the matching stopLinking below removes it again.
                delegates.addForcedVisibleLayer(['switch-linking-layer']);
                delegates.startSwitchPlacing(layoutSwitch);
            }}
            stopLinking={() => {
                delegates.removeForcedVisibleLayer(['switch-linking-layer']);
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
