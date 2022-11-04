import React from 'react';
import { SwitchSuggestionCreatorDialog } from 'linking/switch/switch-suggestion-creator-dialog';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { actionCreators } from 'track-layout/track-layout-store';
import { LocationTrackEndpoint, SuggestedSwitch } from 'linking/linking-model';
import { PublishTypeHandlingDialog } from 'linking/publish-type-handling-dialog';

type SuggestionCreatorData = {
    locationTrackEndPoint: LocationTrackEndpoint;
};

export const SwitchSuggestionCreatorContainer: React.FC = () => {
    const { onSelect, onPublishTypeChange, onLayerVisibilityChange, startSwitchLinking } =
        createDelegates(useTrackLayoutAppDispatch(), actionCreators);
    const state = useTrackLayoutAppSelector((state) => ({
        publishType: state.trackLayout.publishType,
        locationTrackEndPoint: state.trackLayout.selection.selectedItems.locationTrackEndPoints[0],
        locationTrack: state.trackLayout.selection.selectedItems.locationTracks[0],
        locationTrackChangeTime: state.trackLayout.changeTimes.layoutLocationTrack,
    }));

    const suggestionCreatorData: SuggestionCreatorData = state.locationTrackEndPoint && {
        locationTrackEndPoint: state.locationTrackEndPoint,
    };

    function unselectLocationTrackEndPoint() {
        onSelect({
            locationTrackEndPoints: [],
        });
    }

    function closeSuggestionCreatorDialog() {
        unselectLocationTrackEndPoint();
    }

    function onSuggestedSwitchCreated(suggestedSwitch: SuggestedSwitch) {
        onLayerVisibilityChange({
            layerId: 'manualSwitchLinking',
            visible: false,
        });
        onSelect({
            locationTrackEndPoints: [],
            suggestedSwitches: [suggestedSwitch],
        });
        startSwitchLinking(suggestedSwitch);
    }

    return (
        <React.Fragment>
            {suggestionCreatorData && state.publishType != 'DRAFT' && (
                <PublishTypeHandlingDialog
                    onPublishTypeChange={onPublishTypeChange}
                    onClose={closeSuggestionCreatorDialog}
                />
            )}

            {suggestionCreatorData && state.publishType == 'DRAFT' && (
                <SwitchSuggestionCreatorDialog
                    locationTrackEndpoint={suggestionCreatorData.locationTrackEndPoint}
                    onSuggestedSwitchCreated={onSuggestedSwitchCreated}
                    onClose={closeSuggestionCreatorDialog}
                    publishType={state.publishType}
                />
            )}
        </React.Fragment>
    );
};
