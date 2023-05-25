import * as React from 'react';
import ToolPanel from 'tool-panel/tool-panel';
import { MapContext } from 'map/map-store';
import { useAppSelector, useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { LinkingType, SuggestedSwitch } from 'linking/linking-model';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import { getSuggestedSwitchByPoint } from 'linking/linking-api';

const ToolPanelContainer: React.FC = () => {
    const context = React.useContext(MapContext);
    const store = useAppSelector((state) => state[context]);

    const delegates = React.useMemo(() => {
        return createDelegates(TrackLayoutActions);
    }, []);
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
    },
    []);

    const startSwitchPlacing = React.useCallback(function (layoutSwitch: LayoutSwitch) {
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
            geometryKmPosts={store.selection.selectedItems.geometryKmPosts}
            switchIds={switchIds}
            geometrySwitches={store.selection.selectedItems.geometrySwitches}
            locationTrackIds={store.selection.selectedItems.locationTracks}
            geometryAlignments={store.selection.selectedItems.geometryAlignments}
            linkingState={store.linkingState}
            showArea={delegates.showArea}
            changeTimes={changeTimes}
            publishType={store.publishType}
            suggestedSwitches={store.selection.selectedItems.suggestedSwitches}
            onDataChange={typeChange}
            onUnselect={delegates.onUnselect}
            setSelectedTabId={delegates.setToolPanelTab}
            selectedTabId={store.selectedToolPanelTabId}
            startSwitchPlacing={startSwitchPlacing}
            viewport={store.map.viewport}
            stopSwitchLinking={() => {
                delegates.hideLayers(['switch-linking-layer']);
                delegates.stopLinking();
            }}
            verticalGeometryDiagramVisible={store.map.verticalGeometryDiagramVisible}
        />
    );
};

export default ToolPanelContainer;
