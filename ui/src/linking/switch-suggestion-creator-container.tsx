import React from 'react';
import { SwitchSuggestionCreatorDialog } from 'linking/switch/switch-suggestion-creator-dialog';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { LocationTrackEndpoint, SuggestedSwitch } from 'linking/linking-model';
import { PublishTypeHandlingDialog } from 'linking/publish-type-handling-dialog';

type SuggestionCreatorData = {
    locationTrackEndPoint: LocationTrackEndpoint;
};

export const SwitchSuggestionCreatorContainer: React.FC = () => {
    const { onSelect, onPublishTypeChange, onLayerVisibilityChange, startSwitchLinking } =
        createDelegates(trackLayoutActionCreators);
    const locationTrackChangeTime = useCommonDataAppSelector(
        (state) => state.changeTimes.layoutLocationTrack,
    );
    const state = useTrackLayoutAppSelector((state) => ({
        publishType: state.publishType,
        locationTrackEndPoint: state.selection.selectedItems.locationTrackEndPoints[0],
        locationTrack: state.selection.selectedItems.locationTracks[0],
        locationTrackChangeTime: locationTrackChangeTime,
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
            type: 'manualSwitchLinking',
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
