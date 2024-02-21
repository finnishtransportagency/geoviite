import * as React from 'react';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { createDelegates } from 'store/store-utils';
import {
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
    onVisibilityChange: (visibilities: LocationTrackInfoboxVisibilities) => void;
    onHoverOverPlanSection: (item: HighlightedAlignment | undefined) => void;
};

const LocationTrackInfoboxLinkingContainer: React.FC<LocationTrackInfoboxLinkingContainerProps> = ({
    locationTrackId,
    onDataChange,
    visibilities,
    onVisibilityChange,
    onHoverOverPlanSection,
}: LocationTrackInfoboxLinkingContainerProps) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const locationTrack = useLocationTrack(
        locationTrackId,
        trackLayoutState.publishType,
        changeTimes.layoutLocationTrack,
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
                onStartLocationTrackGeometryChange={(interval) => {
                    delegates.showLayers(['alignment-linking-layer']);
                    delegates.startAlignmentGeometryChange(interval);
                }}
                onEndLocationTrackGeometryChange={() => {
                    delegates.hideLayers(['alignment-linking-layer']);
                    delegates.stopLinking();
                }}
                showArea={delegates.showArea}
                publishType={trackLayoutState.publishType}
                locationTrackChangeTime={changeTimes.layoutLocationTrack}
                switchChangeTime={changeTimes.layoutSwitch}
                trackNumberChangeTime={changeTimes.layoutTrackNumber}
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
                showLocationTrackTaskList={delegates.showLocationTrackTaskList}
            />
        );
};

export default LocationTrackInfoboxLinkingContainer;
