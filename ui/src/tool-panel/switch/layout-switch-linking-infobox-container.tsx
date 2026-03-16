import * as React from 'react';
import { SuggestedSwitch } from 'linking/linking-model';
import {
    GeometrySwitchInfoboxVisibilities,
    SwitchLinkingInfoboxVisibilities,
} from 'track-layout/track-layout-slice';
import { LayoutSwitchLinkingInfobox } from 'tool-panel/switch/layout-switch-linking-infobox';
import { useSwitch } from 'track-layout/track-layout-react-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';

type LayoutSwitchLinkingInfoboxContainerProps = {
    layoutSwitchId: LayoutSwitchId;
    suggestedSwitch: SuggestedSwitch;
    visibilities: SwitchLinkingInfoboxVisibilities;
    onVisibilityChange: (visibilities: GeometrySwitchInfoboxVisibilities) => void;
};

export const LayoutSwitchLinkingInfoboxContainer: React.FC<
    LayoutSwitchLinkingInfoboxContainerProps
> = ({ layoutSwitchId, suggestedSwitch, visibilities, onVisibilityChange }) => {
    const layoutContext = useTrackLayoutAppSelector((state) => state.layoutContext);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const layoutSwitch = useSwitch(layoutSwitchId, layoutContext, changeTimes.layoutSwitch);
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const onStopLinking = React.useCallback(() => {
        delegates.removeForcedVisibleLayer(['switch-linking-layer']);
        delegates.stopLinking();
    }, []);

    return (
        layoutSwitch && (
            <LayoutSwitchLinkingInfobox
                layoutSwitch={layoutSwitch}
                layoutContext={layoutContext}
                suggestedSwitch={suggestedSwitch}
                visibilities={visibilities}
                onVisibilityChange={onVisibilityChange}
                onStopLinking={onStopLinking}
            />
        )
    );
};
