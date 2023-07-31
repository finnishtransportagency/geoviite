import SelectionPanel from 'selection-panel/selection-panel';
import { getSelectableItemTypes, trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import * as React from 'react';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import {
    useKmPosts,
    useLocationTracks,
    useReferenceLines,
    useSwitches,
} from 'track-layout/track-layout-react-utils';

export const SelectionPanelContainer: React.FC = () => {
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);
    const state = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const selectableItemTypes = React.useMemo(() => {
        return getSelectableItemTypes(state.linkingState);
    }, [state.linkingState]);

    const locationTracks = useLocationTracks(
        state.map.shownItems.locationTracks,
        state.publishType,
        changeTimes.layoutLocationTrack,
    );
    const referenceLines = useReferenceLines(
        state.map.shownItems.referenceLines,
        state.publishType,
        changeTimes.layoutReferenceLine,
    );
    const switches = useSwitches(
        state.map.shownItems.switches,
        state.publishType,
        changeTimes.layoutSwitch,
    );
    const kmPosts = useKmPosts(
        state.map.shownItems.kmPosts,
        state.publishType,
        changeTimes.layoutKmPost,
    );
    return (
        <SelectionPanel
            onSelect={delegates.onSelect}
            onTogglePlanVisibility={delegates.togglePlanVisibility}
            onToggleAlignmentVisibility={delegates.toggleAlignmentVisibility}
            onToggleSwitchVisibility={delegates.toggleSwitchVisibility}
            onToggleKmPostVisibility={delegates.toggleKmPostsVisibility}
            changeTimes={changeTimes}
            publishType={state.publishType}
            selectedItems={state.selection.selectedItems}
            visiblePlans={state.selection.visiblePlans}
            kmPosts={kmPosts}
            referenceLines={referenceLines}
            locationTracks={locationTracks}
            switches={switches}
            viewport={state.map.viewport}
            selectableItemTypes={selectableItemTypes}
            togglePlanOpen={delegates.togglePlanOpen}
            openedPlanLayouts={state.selection.openedPlanLayouts}
            togglePlanKmPostsOpen={delegates.togglePlanKmPostsOpen}
            togglePlanAlignmentsOpen={delegates.togglePlanAlignmentsOpen}
            togglePlanSwitchesOpen={delegates.togglePlanSwitchesOpen}
            onMapLayerSettingChange={delegates.onLayerSettingChange}
            mapLayerSettings={state.map.layerSettings}
            mapLayoutMenu={state.map.layerMenu.layout}
            onMapLayerMenuItemChange={delegates.onLayerMenuItemChange}
        />
    );
};
