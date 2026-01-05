import * as React from 'react';
import ToolPanel from 'tool-panel/tool-panel';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import {
    canGoBackInSelectionHistory,
    canGoForwardInSelectionHistory,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { LinkingType, SuggestedSwitch } from 'linking/linking-model';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import { getSuggestedSwitchForLayoutSwitchPlacing } from 'linking/linking-api';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';

type ToolPanelContainerProps = {
    setHoveredOverItem: (item: HighlightedAlignment | undefined) => void;
};

const ToolPanelContainer: React.FC<ToolPanelContainerProps> = ({ setHoveredOverItem }) => {
    const store = useTrackLayoutAppSelector((state) => state);

    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const typeChange = React.useCallback(
        () => delegates.onPublicationStateChange('DRAFT'),
        [delegates],
    );
    const kmPostIds = store.selection.selectedItems.kmPosts;
    const switchIds = store.selection.selectedItems.switches;
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const startSwitchLinking = React.useCallback(function (
        suggestedSwitch: SuggestedSwitch,
        layoutSwitch: LayoutSwitch,
    ) {
        delegates.startLayoutSwitchLinking({ suggestedSwitch, layoutSwitch });
        delegates.onSelect({
            switches: [layoutSwitch.id],
        });
        delegates.showLayers(['switch-linking-layer']);
    }, []);

    const infoboxVisibilities = useTrackLayoutAppSelector((state) => state.infoboxVisibilities);

    React.useEffect(() => {
        const linkingState = store.linkingState;
        if (linkingState?.type === LinkingType.PlacingLayoutSwitch && linkingState.location) {
            getSuggestedSwitchForLayoutSwitchPlacing(
                store.layoutContext.branch,
                linkingState.location,
                linkingState.layoutSwitch.id,
            ).then((suggestedSwitch) => {
                delegates.stopLinking();

                if (suggestedSwitch) {
                    startSwitchLinking(suggestedSwitch, linkingState.layoutSwitch);
                } else {
                    delegates.hideLayers(['switch-linking-layer']);
                }
            });
        }
    }, [store.linkingState]);

    return (
        <ToolPanel
            infoboxVisibilities={infoboxVisibilities}
            onInfoboxVisibilityChange={delegates.onInfoboxVisibilityChange}
            planIds={store.selection.selectedItems.geometryPlans}
            trackNumberIds={store.selection.selectedItems.trackNumbers}
            kmPostIds={kmPostIds}
            geometryKmPostIds={store.selection.selectedItems.geometryKmPostIds}
            switchIds={switchIds}
            geometrySwitchIds={store.selection.selectedItems.geometrySwitchIds}
            locationTrackIds={store.selection.selectedItems.locationTracks}
            geometryAlignmentIds={store.selection.selectedItems.geometryAlignmentIds}
            operationalPointIds={store.selection.selectedItems.operationalPoints}
            linkingState={store.linkingState}
            splittingState={store.splittingState}
            changeTimes={changeTimes}
            layoutContext={store.layoutContext}
            onDataChange={typeChange}
            setSelectedAsset={delegates.setToolPanelTab}
            selectedAsset={store.selectedToolPanelTab}
            viewport={store.map.viewport}
            verticalGeometryDiagramVisible={store.map.verticalGeometryDiagramState.visible}
            onHoverOverPlanSection={setHoveredOverItem}
            onSelectionHistoryBack={delegates.onSelectHistoryBack}
            onSelectionHistoryForward={delegates.onSelectHistoryForward}
            selectionHistoryBackEnabled={canGoBackInSelectionHistory(store)}
            selectionHistoryForwardEnabled={canGoForwardInSelectionHistory(store)}
        />
    );
};

export default ToolPanelContainer;
