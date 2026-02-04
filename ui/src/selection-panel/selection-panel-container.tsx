import SelectionPanel from 'selection-panel/selection-panel';
import { getSelectableItemTypes, trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import * as React from 'react';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import {
    useKmPosts,
    useLocationTracks,
    useOperationalPoints,
    useReferenceLines,
    useSwitches,
} from 'track-layout/track-layout-react-utils';
import { planDownloadAssetIdFromToolPanelAsset } from 'map/plan-download/plan-download-store';

type SelectionPanelContainerProps = {
    setSwitchToOfficialDialogOpen: (open: boolean) => void;
};

export const SelectionPanelContainer: React.FC<SelectionPanelContainerProps> = ({
    setSwitchToOfficialDialogOpen,
}) => {
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);
    const state = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const selectableItemTypes = React.useMemo(() => {
        return getSelectableItemTypes(state.splittingState, state.linkingState);
    }, [state.linkingState]);

    const locationTracks = useLocationTracks(
        state.map.shownItems.locationTracks,
        state.layoutContext,
        changeTimes.layoutLocationTrack,
    );
    const referenceLines = useReferenceLines(
        state.map.shownItems.referenceLines,
        state.layoutContext,
        changeTimes.layoutReferenceLine,
    );
    const switches = useSwitches(
        state.map.shownItems.switches,
        state.layoutContext,
        changeTimes.layoutSwitch,
    );
    const kmPosts = useKmPosts(
        state.map.shownItems.kmPosts,
        state.layoutContext,
        changeTimes.layoutKmPost,
    );

    const allOperationalPoints = useOperationalPoints(
        state.layoutContext,
        changeTimes.operationalPoints,
    );

    const operationalPoints = React.useMemo(() => {
        const viewport = state.map.viewport;
        if (!viewport.area) {
            return [];
        }

        const viewportArea = viewport.area;
        return allOperationalPoints.filter((op) => {
            if (!op.location) {
                return false;
            }
            const { x, y } = op.location;
            return (
                x >= viewportArea.x.min &&
                x <= viewportArea.x.max &&
                y >= viewportArea.y.min &&
                y <= viewportArea.y.max
            );
        });
    }, [allOperationalPoints, state.map.viewport]);

    const togglePlanDownload = () => {
        if (state.planDownloadState) {
            delegates.onClosePlanDownloadPopup();
        } else if (state.layoutContext.publicationState === 'DRAFT') {
            setSwitchToOfficialDialogOpen(true);
        } else {
            const initialAsset = state.selectedToolPanelTab
                ? planDownloadAssetIdFromToolPanelAsset(state.selectedToolPanelTab)
                : undefined;
            delegates.onOpenPlanDownloadPopup(initialAsset);
        }
    };

    return (
        <SelectionPanel
            onSelect={delegates.onSelect}
            onTogglePlanVisibility={delegates.togglePlanVisibility}
            onToggleAlignmentVisibility={delegates.toggleAlignmentVisibility}
            onToggleSwitchVisibility={delegates.toggleSwitchVisibility}
            onToggleKmPostVisibility={delegates.toggleKmPostsVisibility}
            changeTimes={changeTimes}
            layoutContext={state.layoutContext}
            selectedItems={state.selection.selectedItems}
            visiblePlans={state.selection.visiblePlans}
            kmPosts={kmPosts}
            referenceLines={referenceLines}
            locationTracks={locationTracks}
            switches={switches}
            operationalPoints={operationalPoints}
            viewport={state.map.viewport}
            selectableItemTypes={selectableItemTypes}
            togglePlanOpen={delegates.togglePlanOpen}
            openPlans={state.selection.openPlans}
            togglePlanKmPostsOpen={delegates.togglePlanKmPostsOpen}
            togglePlanAlignmentsOpen={delegates.togglePlanAlignmentsOpen}
            togglePlanSwitchesOpen={delegates.togglePlanSwitchesOpen}
            onMapLayerSettingChange={delegates.onLayerSettingChange}
            mapLayerSettings={state.map.layerSettings}
            mapLayoutMenu={state.map.layerMenu.layout}
            onMapLayerMenuItemChange={delegates.onLayerMenuItemChange}
            splittingState={state.splittingState}
            grouping={state.geometryPlanViewSettings.grouping}
            visibleSources={state.geometryPlanViewSettings.visibleSources}
            togglePlanDownloadPopupOpen={togglePlanDownload}
            planDownloadPopupOpen={!!state.planDownloadState}
        />
    );
};
