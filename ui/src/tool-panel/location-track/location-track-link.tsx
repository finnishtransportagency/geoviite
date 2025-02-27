import { LocationTrackId } from 'track-layout/track-layout-model';
import React from 'react';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

export type LocationTrackLinkProps = {
    locationTrackId: LocationTrackId;
    locationTrackName: string;
};

export const LocationTrackLink: React.FC<LocationTrackLinkProps> = (
    props: LocationTrackLinkProps,
) => {
    const delegates = createDelegates(TrackLayoutActions);

    return (
        <AnchorLink
            onClick={() => {
                delegates.onSelect({ locationTracks: [props.locationTrackId] });
                delegates.setToolPanelTab({ id: props.locationTrackId, type: 'LOCATION_TRACK' });
            }}>
            {props.locationTrackName}
        </AnchorLink>
    );
};
