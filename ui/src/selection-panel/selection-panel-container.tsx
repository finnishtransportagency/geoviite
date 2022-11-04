import SelectionPanel from 'selection-panel/selection-panel';
import { actionCreators, getSelectableItemTypes } from 'track-layout/track-layout-store';
import { createDelegates } from 'store/store-utils';
import * as React from 'react';
import { MapContext } from 'map/map-store';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';

export const SelectionPanelContainer: React.FC = () => {
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = React.useMemo(() => {
        return createDelegates(dispatch, actionCreators);
    }, []);
    const context = React.useContext(MapContext);
    const store = useTrackLayoutAppSelector((state) => state[context]);

    const selectableItemTypes = React.useMemo(() => {
        return getSelectableItemTypes(store.linkingState);
    }, [store.linkingState]);

    return (
        <SelectionPanel
            onSelect={delegates.onSelect}
            onTogglePlanVisibility={delegates.togglePlanVisibility}
            onToggleAlignmentVisibility={delegates.toggleAlignmentVisibility}
            onToggleSwitchVisibility={delegates.toggleSwitchVisibility}
            onToggleKmPostVisibility={delegates.toggleKmPostsVisibility}
            changeTimes={store.changeTimes}
            publishType={store.publishType}
            selectedItems={store.selection.selectedItems}
            selectedPlanLayouts={store.selection.planLayouts}
            kmPosts={store.map.shownItems.kmPosts}
            referenceLines={store.map.shownItems.referenceLines}
            locationTracks={store.map.shownItems.locationTracks}
            switches={store.map.shownItems.switches}
            viewport={store.map.viewport}
            selectableItemTypes={selectableItemTypes}
            togglePlanOpen={delegates.togglePlanOpen}
            openedPlanLayouts={store.selection.openedPlanLayouts}
            togglePlanKmPostsOpen={delegates.togglePlanKmPostsOpen}
            togglePlanAlignmentsOpen={delegates.togglePlanAlignmentsOpen}
            togglePlanSwitchesOpen={delegates.togglePlanSwitchesOpen}></SelectionPanel>
    );
};
