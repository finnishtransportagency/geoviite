import * as React from 'react';
import styles from './alignment-badge.scss';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';
import { createClassName } from 'vayla-design-lib/utils';
import { AlignmentHeader } from 'track-layout/layout-map-api';

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
    const classes = createClassName(
        styles['alignment-badge'],
        status,
        onClick && styles['alignment-badge--clickable'],
    );

    return (
        <div className={classes} onClick={onClick}>
            <span>{locationTrack.name}</span>
        </div>
    );
};
