import * as React from 'react';
import LocationTrackInfobox from 'tool-panel/location-track/location-track-infobox';
import { LayoutLocationTrack, LocationTrackId } from 'track-layout/track-layout-model';
import { LinkingState } from 'linking/linking-model';
import { createDelegates } from 'store/store-utils';
import {
    LocationTrackInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { PublishType, TimeStamp } from 'common/common-model';
import { useLocationTrack } from 'track-layout/track-layout-react-utils';
import { MapViewport } from 'map/map-model';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';

type LocationTrackInfoboxLinkingContainerProps = {
    locationTrackId: LocationTrackId;
    linkingState?: LinkingState;
    publishType: PublishType;
    locationTrackChangeTime: TimeStamp;
    onUnselect: (track: LayoutLocationTrack) => void;
    onDataChange: () => void;
    viewport: MapViewport;
    visibilities: LocationTrackInfoboxVisibilities;
    onVisibilityChange: (visibilities: LocationTrackInfoboxVisibilities) => void;
    verticalGeometryDiagramVisible: boolean;
    onHoverOverPlanSection: (item: HighlightedAlignment | undefined) => void;
};

const LocationTrackInfoboxLinkingContainer: React.FC<LocationTrackInfoboxLinkingContainerProps> = ({
    locationTrackId,
    linkingState,
    publishType,
    locationTrackChangeTime,
    onUnselect,
    onDataChange,
    viewport,
    visibilities,
    onVisibilityChange,
    verticalGeometryDiagramVisible,
    onHoverOverPlanSection,
}: LocationTrackInfoboxLinkingContainerProps) => {
    const delegates = createDelegates(TrackLayoutActions);
    const locationTrack = useLocationTrack(locationTrackId, publishType, locationTrackChangeTime);

    if (!locationTrack) return <></>;
    else
        return (
            <LocationTrackInfobox
                visibilities={visibilities}
                onVisibilityChange={onVisibilityChange}
                locationTrack={locationTrack}
                linkingState={linkingState}
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
                publishType={publishType}
                locationTrackChangeTime={locationTrackChangeTime}
                onUnselect={onUnselect}
                onSelect={delegates.onSelect}
                viewport={viewport}
                onVerticalGeometryDiagramVisibilityChange={
                    delegates.onVerticalGeometryDiagramVisibilityChange
                }
                verticalGeometryDiagramVisible={verticalGeometryDiagramVisible}
                onHighlightItem={onHoverOverPlanSection}
            />
        );
};

export default LocationTrackInfoboxLinkingContainer;
