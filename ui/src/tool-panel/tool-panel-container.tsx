import * as React from 'react';
import ToolPanel from 'tool-panel/tool-panel';
import { MapContext } from 'map/map-store';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
import { createDelegates } from 'store/store-utils';

const ToolPanelContainer: React.FC = () => {
    const context = React.useContext(MapContext);
    const store = useTrackLayoutAppSelector((state) => state[context]);

    const dispatch = useTrackLayoutAppDispatch();
    const delegates = React.useMemo(() => {
        return createDelegates(dispatch, TrackLayoutActions);
    }, []);
    const typeChange = React.useCallback(() => delegates.onPublishTypeChange('DRAFT'), [delegates]);
    // TODO Remove these memoizations when selection only has ids for switches and km posts as well
    const kmPostIds = React.useMemo(
        () => store.selection.selectedItems.kmPosts.map((kmPost) => kmPost.id),
        [store.selection.selectedItems.kmPosts],
    );
    const switchIds = React.useMemo(
        () => store.selection.selectedItems.switches.map((sw) => sw.id),
        [store.selection.selectedItems.switches],
    );

    return (
        <ToolPanel
            planHeaders={store.selection.selectedItems.geometryPlans}
            trackNumberIds={store.selection.selectedItems.trackNumbers}
            kmPostIds={kmPostIds}
            geometryKmPosts={store.selection.selectedItems.geometryKmPosts}
            switchIds={switchIds}
            geometrySwitches={store.selection.selectedItems.geometrySwitches}
            locationTrackIds={store.selection.selectedItems.locationTracks}
            referenceLineIds={store.selection.selectedItems.referenceLines}
            geometryAlignments={store.selection.selectedItems.geometryAlignments}
            geometrySegments={store.selection.selectedItems.geometrySegments}
            linkingState={store.linkingState}
            showArea={delegates.showArea}
            changeTimes={store.changeTimes}
            publishType={store.publishType}
            suggestedSwitches={store.selection.selectedItems.suggestedSwitches}
            onDataChange={typeChange}
            onUnselect={delegates.onUnselect}
            setSelectedTabId={delegates.setToolPanelTab}
            selectedTabId={store.selectedToolPanelTabId}
        />
    );
};

export default ToolPanelContainer;
