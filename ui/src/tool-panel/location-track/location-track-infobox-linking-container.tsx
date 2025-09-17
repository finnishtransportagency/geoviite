import * as React from 'react';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { createDelegates } from 'store/store-utils';
import {
    InfoboxVisibilities,
    LocationTrackInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { useLocationTrack } from 'track-layout/track-layout-react-utils';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import LocationTrackInfobox from './location-track-infobox';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';

type LocationTrackInfoboxLinkingContainerProps = {
    locationTrackId: LocationTrackId;
    onDataChange: () => void;
    visibilities: LocationTrackInfoboxVisibilities;
    onInfoboxVisibilityChange: (
        key: keyof InfoboxVisibilities,
        visibilities: LocationTrackInfoboxVisibilities,
    ) => void;
    onHoverOverPlanSection: (item: HighlightedAlignment | undefined) => void;
};

const LocationTrackInfoboxLinkingContainer: React.FC<LocationTrackInfoboxLinkingContainerProps> = ({
    locationTrackId,
    onDataChange,
    visibilities,
    onInfoboxVisibilityChange,
    onHoverOverPlanSection,
}: LocationTrackInfoboxLinkingContainerProps) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const locationTrack = useLocationTrack(
        locationTrackId,
        trackLayoutState.layoutContext,
        changeTimes.layoutLocationTrack,
    );

    const onVisibilityChange = React.useCallback(
        (visibilities: LocationTrackInfoboxVisibilities) => {
            onInfoboxVisibilityChange('locationTrack', visibilities);
        },
        [onInfoboxVisibilityChange, visibilities],
    );

    if (!locationTrack) return <></>;
    else
        return (
            <LocationTrackInfobox
                visibilities={visibilities}
                onVisibilityChange={onVisibilityChange}
                locationTrack={locationTrack}
                linkingState={trackLayoutState.linkingState}
                splittingState={trackLayoutState.splittingState}
                onDataChange={onDataChange}
                showArea={delegates.showArea}
                layoutContext={trackLayoutState.layoutContext}
                changeTimes={changeTimes}
                onSelect={delegates.onSelect}
                onUnselect={delegates.onUnselect}
                viewport={trackLayoutState.map.viewport}
                onVerticalGeometryDiagramVisibilityChange={
                    delegates.onVerticalGeometryDiagramVisibilityChange
                }
                verticalGeometryDiagramVisible={
                    trackLayoutState.map.verticalGeometryDiagramState.visible
                }
                onHighlightItem={onHoverOverPlanSection}
            />
        );
};

export default LocationTrackInfoboxLinkingContainer;
