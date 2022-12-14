import * as React from 'react';
import { useTrackLayoutAppDispatch } from 'store/hooks';
import LocationTrackInfobox from 'tool-panel/location-track/location-track-infobox';
import { LayoutLocationTrack, LocationTrackId } from 'track-layout/track-layout-model';
import { LinkingState } from 'linking/linking-model';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
import { PublishType, TimeStamp } from 'common/common-model';
import { useLocationTrack } from 'track-layout/track-layout-react-utils';

type LocationTrackInfoboxLinkingContainerProps = {
    locationTrackId: LocationTrackId;
    linkingState?: LinkingState;
    publishType: PublishType;
    locationTrackChangeTime: TimeStamp;
    onUnselect: (track: LayoutLocationTrack) => void;
    onDataChange: () => void;
};

const LocationTrackInfoboxLinkingContainer: React.FC<LocationTrackInfoboxLinkingContainerProps> = ({
    locationTrackId,
    linkingState,
    publishType,
    locationTrackChangeTime,
    onUnselect,
    onDataChange,
}: LocationTrackInfoboxLinkingContainerProps) => {
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);
    const locationTrack = useLocationTrack(locationTrackId, publishType, locationTrackChangeTime);

    if (!locationTrack) return <></>;
    else
        return (
            <LocationTrackInfobox
                locationTrack={locationTrack}
                linkingState={linkingState}
                onDataChange={onDataChange}
                onStartLocationTrackGeometryChange={delegates.startAlignmentGeometryChange}
                onEndLocationTrackGeometryChange={delegates.stopLinking}
                showArea={delegates.showArea}
                publishType={publishType}
                locationTrackChangeTime={locationTrackChangeTime}
                onUnselect={onUnselect}
                onSelect={delegates.onSelect}
            />
        );
};

export default LocationTrackInfoboxLinkingContainer;
