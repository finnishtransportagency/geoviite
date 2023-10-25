import * as React from 'react';
import ToolPanel from 'tool-panel/tool-panel';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { LinkingType, SuggestedSwitch } from 'linking/linking-model';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import { getSuggestedSwitchByPoint } from 'linking/linking-api';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';

type ToolPanelContainerProps = {
    setHoveredOverItem: (item: HighlightedAlignment | undefined) => void;
};

const ToolPanelContainer: React.FC<ToolPanelContainerProps> = ({ setHoveredOverItem }) => {
    const store = useTrackLayoutAppSelector((state) => state);

    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const typeChange = React.useCallback(() => delegates.onPublishTypeChange('DRAFT'), [delegates]);
    const kmPostIds = store.selection.selectedItems.kmPosts;
    const switchIds = store.selection.selectedItems.switches;
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const startSwitchLinking = React.useCallback(function (
        suggestedSwitch: SuggestedSwitch,
        layoutSwitch: LayoutSwitch,
    ) {
        delegates.onSelect({
            suggestedSwitches: [suggestedSwitch],
        });
        delegates.startSwitchLinking(suggestedSwitch);
        delegates.onSelect({
            switches: [layoutSwitch.id],
        });
    }, []);

    const startSwitchPlacing = React.useCallback(function (layoutSwitch: LayoutSwitch) {
        delegates.showLayers(['switch-linking-layer']);
        delegates.startSwitchPlacing(layoutSwitch);
    }, []);

    const infoboxVisibilities = useTrackLayoutAppSelector((state) => state.infoboxVisibilities);

    React.useEffect(() => {
        const linkingState = store.linkingState;
        if (linkingState?.type == LinkingType.PlacingSwitch && linkingState.location) {
            getSuggestedSwitchByPoint(
                linkingState.location,
                linkingState.layoutSwitch.switchStructureId,
            ).then((suggestedSwitches) => {
                delegates.stopLinking();
                if (suggestedSwitches.length) {
                    startSwitchLinking(suggestedSwitches[0], linkingState.layoutSwitch);
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
            linkingState={store.linkingState}
            showArea={delegates.showArea}
            changeTimes={changeTimes}
            publishType={store.publishType}
            suggestedSwitches={store.selection.selectedItems.suggestedSwitches}
            onDataChange={typeChange} // TODO: GVT-2173 WTF is this?
            onSelect={delegates.onSelect}
            onUnselect={delegates.onUnselect}
            setSelectedAsset={delegates.setToolPanelTab}
            selectedAsset={store.selectedToolPanelTab}
            startSwitchPlacing={startSwitchPlacing}
            viewport={store.map.viewport}
            stopSwitchLinking={() => {
                delegates.hideLayers(['switch-linking-layer']);
                delegates.stopLinking();
            }}
            verticalGeometryDiagramVisible={store.map.verticalGeometryDiagramState.visible}
            onHoverOverPlanSection={setHoveredOverItem}
        />
    );
};

export default ToolPanelContainer;
