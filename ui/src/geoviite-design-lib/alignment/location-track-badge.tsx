import * as React from 'react';
import styles from './alignment-badge.scss';
import { LayoutLocationTrack, LocationTrackId } from 'track-layout/track-layout-model';
import { createClassName } from 'vayla-design-lib/utils';
import { AlignmentHeader } from 'track-layout/layout-map-api';
import { LayoutContext, TimeStamp } from 'common/common-model';
import { useLocationTrack } from 'track-layout/track-layout-react-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';

type LocationTrackBadgeProps = {
    locationTrack: AlignmentHeader | LayoutLocationTrack;
    onClick?: React.MouseEventHandler;
    status?: LocationTrackBadgeStatus;
};

export enum LocationTrackBadgeStatus {
    DEFAULT = 'alignment-badge--default',
    LINKED = 'alignment-badge--linked',
    UNLINKED = 'alignment-badge--unlinked',
    SELECTED = 'alignment-badge--selected',
    DISABLED = 'alignment-badge--disabled',
}

export const LocationTrackBadge: React.FC<LocationTrackBadgeProps> = ({
    locationTrack,
    onClick,
    status = LocationTrackBadgeStatus.DEFAULT,
}: LocationTrackBadgeProps) => {
    const disabled = status === LocationTrackBadgeStatus.DISABLED;
    const classes = createClassName(
        styles['alignment-badge'],
        status,
        !disabled && onClick && styles['alignment-badge--clickable'],
    );

    return (
        <div className={classes} onClick={!disabled ? onClick : undefined}>
            <span>{locationTrack.name}</span>
        </div>
    );
};

type LocationTrackBadgeLinkProps = {
    locationTrackId: LocationTrackId;
    layoutContext: LayoutContext;
    changeTime: TimeStamp;
    onClick?: (locationTrackId: LocationTrackId) => void;
    status?: LocationTrackBadgeStatus;
};

export const LocationTrackBadgeLink: React.FC<LocationTrackBadgeLinkProps> = ({
    locationTrackId,
    layoutContext,
    changeTime,
    onClick,
    status,
}: LocationTrackBadgeLinkProps) => {
    const locationTrack = useLocationTrack(locationTrackId, layoutContext, changeTime);

    const clickAction = React.useCallback(() => {
        if (onClick) {
            onClick(locationTrackId);
        } else {
            const delegates = createDelegates(TrackLayoutActions);
            delegates.onSelect({
                locationTracks: [locationTrackId],
                selectedTab: { id: locationTrackId, type: 'LOCATION_TRACK' },
            });
        }
    }, [onClick, locationTrackId]);

    return locationTrack ? (
        <LocationTrackBadge locationTrack={locationTrack} onClick={clickAction} status={status} />
    ) : (
        <Spinner />
    );
};
