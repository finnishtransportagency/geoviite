import { LocationTrackId } from 'track-layout/track-layout-model';
import { Link } from 'vayla-design-lib/link/link';
import React from 'react';
import { useTrackLayoutAppDispatch } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';

export type LocationTrackLinkProps = {
    locationTrackId: LocationTrackId;
    locationTrackName: string;
};

export const LocationTrackLink: React.FC<LocationTrackLinkProps> = (
    props: LocationTrackLinkProps,
) => {
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);

    return (
        <Link onClick={() => delegates.onSelect({ locationTracks: [props.locationTrackId] })}>
            {props.locationTrackName}
        </Link>
    );
};
