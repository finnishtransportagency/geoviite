import SelectionPanel from 'selection-panel/selection-panel';
import { actionCreators, getSelectableItemTypes } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import * as React from 'react';
import { MapContext } from 'map/map-store';
import { useAppSelector, useAppDispatch } from 'store/hooks';
import {
    useKmPosts,
    useLocationTracks,
    useReferenceLines,
    useSwitches,
} from 'track-layout/track-layout-react-utils';

export const SelectionPanelContainer: React.FC = () => {
    const dispatch = useAppDispatch();
    const delegates = React.useMemo(() => {
        return createDelegates(dispatch, actionCreators);
    }, []);
    const context = React.useContext(MapContext);
    const store = useAppSelector((state) => state[context]);

    const selectableItemTypes = React.useMemo(() => {
        return getSelectableItemTypes(store.linkingState);
    }, [store.linkingState]);

    const locationTracks = useLocationTracks(
        store.map.shownItems.locationTracks,
        store.publishType,
        store.changeTimes.layoutLocationTrack,
    );
    const referenceLines = useReferenceLines(
        store.map.shownItems.referenceLines,
        store.publishType,
        store.changeTimes.layoutReferenceLine,
    );
    const switches = useSwitches(
        store.map.shownItems.switches,
        store.publishType,
        store.changeTimes.layoutSwitch,
    );
    const kmPosts = useKmPosts(
        store.map.shownItems.kmPosts,
        store.publishType,
        store.changeTimes.layoutKmPost,
    );
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
            kmPosts={kmPosts}
            referenceLines={referenceLines}
            locationTracks={locationTracks}
            switches={switches}
            viewport={store.map.viewport}
            selectableItemTypes={selectableItemTypes}
            togglePlanOpen={delegates.togglePlanOpen}
            openedPlanLayouts={store.selection.openedPlanLayouts}
            togglePlanKmPostsOpen={delegates.togglePlanKmPostsOpen}
            togglePlanAlignmentsOpen={delegates.togglePlanAlignmentsOpen}
            togglePlanSwitchesOpen={delegates.togglePlanSwitchesOpen}></SelectionPanel>
    );
};
