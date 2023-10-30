import * as React from 'react';
import { LocationTrackId } from 'track-layout/track-layout-model';
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
import { SplittingState } from 'tool-panel/location-track/split-store';
import LocationTrackInfobox from './location-track-infobox';

type LocationTrackInfoboxLinkingContainerProps = {
    locationTrackId: LocationTrackId;
    linkingState?: LinkingState;
    splittingState?: SplittingState;
    publishType: PublishType;
    locationTrackChangeTime: TimeStamp;
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
    splittingState,
    publishType,
    locationTrackChangeTime,
    onDataChange,
    viewport,
    visibilities,
    onVisibilityChange,
    verticalGeometryDiagramVisible,
    onHoverOverPlanSection,
}: LocationTrackInfoboxLinkingContainerProps) => {
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const locationTrack = useLocationTrack(locationTrackId, publishType, locationTrackChangeTime);

    if (!locationTrack) return <></>;
    else
        return (
            <LocationTrackInfobox
                visibilities={visibilities}
                onVisibilityChange={onVisibilityChange}
                locationTrack={locationTrack}
                linkingState={linkingState}
                splittingState={splittingState}
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
                onSelect={delegates.onSelect}
                onUnselect={delegates.onUnselect}
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
