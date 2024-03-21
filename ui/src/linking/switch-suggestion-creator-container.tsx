import React from 'react';
import { SwitchSuggestionCreatorDialog } from 'linking/switch/switch-suggestion-creator-dialog';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { LocationTrackEndpoint, SuggestedSwitch } from 'linking/linking-model';
import { PublicationStateHandlingDialog } from 'linking/publication-state-handling-dialog';
import { first } from 'utils/array-utils';

type SuggestionCreatorData = {
    locationTrackEndPoint: LocationTrackEndpoint;
};

export const SwitchSuggestionCreatorContainer: React.FC = () => {
    const { onSelect, onPublicationStateChange, startSwitchLinking } = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );
    const locationTrackChangeTime = useCommonDataAppSelector(
        (state) => state.changeTimes.layoutLocationTrack,
    );
    const state = useTrackLayoutAppSelector((state) => ({
        layoutContext: state.layoutContext,
        locationTrackEndPoint: first(state.selection.selectedItems.locationTrackEndPoints),
        locationTrack: first(state.selection.selectedItems.locationTracks),
        locationTrackChangeTime: locationTrackChangeTime,
    }));

    const suggestionCreatorData: SuggestionCreatorData | undefined =
        state.locationTrackEndPoint && {
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
        onSelect({
            locationTrackEndPoints: [],
            suggestedSwitches: [suggestedSwitch],
        });
        startSwitchLinking(suggestedSwitch);
    }

    return (
        <React.Fragment>
            {suggestionCreatorData && state.layoutContext.publicationState !== 'DRAFT' && (
                <PublicationStateHandlingDialog
                    onPublicationStateChange={onPublicationStateChange}
                    onClose={closeSuggestionCreatorDialog}
                />
            )}

            {suggestionCreatorData && state.layoutContext.publicationState === 'DRAFT' && (
                <SwitchSuggestionCreatorDialog
                    locationTrackEndpoint={suggestionCreatorData.locationTrackEndPoint}
                    onSuggestedSwitchCreated={onSuggestedSwitchCreated}
                    onClose={closeSuggestionCreatorDialog}
                    layoutContext={state.layoutContext}
                />
            )}
        </React.Fragment>
    );
};
