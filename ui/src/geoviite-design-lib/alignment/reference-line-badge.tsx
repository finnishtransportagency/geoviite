import * as React from 'react';
import styles from './alignment-badge.scss';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { createClassName } from 'vayla-design-lib/utils';

type ReferenceLineBadgeProps = {
    trackNumber: LayoutTrackNumber;
    onClick?: React.MouseEventHandler;
    status?: ReferenceLineBadgeStatus;
};

export enum ReferenceLineBadgeStatus {
    DEFAULT = 'alignment-badge--default',
    LINKED = 'alignment-badge--linked',
    UNLINKED = 'alignment-badge--unlinked',
    SELECTED = 'alignment-badge--selected',
    DISABLED = 'alignment-badge--disabled',
}

export const ReferenceLineBadge: React.FC<ReferenceLineBadgeProps> = ({
    trackNumber,
    onClick,
    status = ReferenceLineBadgeStatus.DEFAULT,
}: ReferenceLineBadgeProps) => {
    const classes = createClassName(
        styles['alignment-badge'],
        styles['alignment-badge--reference'],
        status,
        onClick && styles['alignment-badge--clickable'],
    );

    return (
        <div className={classes} onClick={onClick}>
            <span>{trackNumber.number}</span>
        </div>
    );
};
