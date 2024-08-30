import * as React from 'react';
import ToolPanel from 'tool-panel/tool-panel';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { LinkingType } from 'linking/linking-model';

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

    const infoboxVisibilities = useTrackLayoutAppSelector((state) => state.infoboxVisibilities);
    /*
    React.useEffect(() => {
        const linkingState = store.linkingState;
        if (linkingState?.type == LinkingType.SuggestingSwitchPlace) {
            const suggestedSwitch = linkingState.suggestedSwitch;
            const layoutSwitch = linkingState.layoutSwitch;
            delegates.onSelect({
                suggestedSwitches: [suggestedSwitch],
            });
            delegates.startSwitchLinking(suggestedSwitch);
            delegates.onSelect({
                switches: [layoutSwitch.id],
            });
            delegates.showLayers(['switch-linking-layer']);
        }
    }, [store.linkingState]);
*/

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
            splittingState={store.splittingState}
            changeTimes={changeTimes}
            layoutContext={store.layoutContext}
            suggestedSwitches={store.selection.selectedItems.suggestedSwitches}
            onDataChange={typeChange}
            setSelectedAsset={delegates.setToolPanelTab}
            selectedAsset={store.selectedToolPanelTab}
            viewport={store.map.viewport}
            verticalGeometryDiagramVisible={store.map.verticalGeometryDiagramState.visible}
            onHoverOverPlanSection={setHoveredOverItem}
        />
    );
};

export default ToolPanelContainer;
