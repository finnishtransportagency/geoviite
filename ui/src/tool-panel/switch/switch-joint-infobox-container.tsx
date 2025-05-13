import * as React from 'react';
import SwitchJointInfobox from 'tool-panel/switch/switch-joint-infobox';
import { SuggestedSwitch } from 'linking/linking-model';
import { LayoutContext, SwitchStructure } from 'common/common-model';
import { LocationTrackId } from 'track-layout/track-layout-model';

type SwitchJointInfoboxContainerProps = {
    suggestedSwitch: SuggestedSwitch;
    suggestedSwitchStructure: SwitchStructure;
    layoutContext: LayoutContext;
    onSelectLocationTrackBadge?: (locationTrackId: LocationTrackId) => void;
};

export const SwitchJointInfoboxContainer: React.FC<SwitchJointInfoboxContainerProps> = ({
    suggestedSwitch,
    suggestedSwitchStructure,
    layoutContext,
    onSelectLocationTrackBadge,
}) => (
    // this is currently a trivial container
    <SwitchJointInfobox
        suggestedSwitch={suggestedSwitch}
        suggestedSwitchStructure={suggestedSwitchStructure}
        layoutContext={layoutContext}
        onSelectLocationTrackBadge={onSelectLocationTrackBadge}
    />
);
