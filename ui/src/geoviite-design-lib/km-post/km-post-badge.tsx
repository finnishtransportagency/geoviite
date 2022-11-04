import * as React from 'react';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import styles from './km-post-badge.scss';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { createClassName } from 'vayla-design-lib/utils';

type KmPostBadgeProps = {
    kmPost: LayoutKmPost;
    status?: KmPostBadgeStatus;
    onClick?: () => void;
};

export enum KmPostBadgeStatus {
    DEFAULT = 'km-post-badge--default',
    LINKED = 'km-post-badge--linked',
    UNLINKED = 'km-post-badge--unlinked',
    SELECTED = 'km-post-badge--selected',
    DISABLED = 'km-post-badge--disabled',
}

export const KmPostBadge: React.FC<KmPostBadgeProps> = ({
    kmPost,
    status = KmPostBadgeStatus.DEFAULT,
    onClick,
}: KmPostBadgeProps) => {
    const classes = createClassName(
        styles['km-post-badge'],
        status,
        onClick && styles['km-post-badge--clickable'],
    );

    const Icon = status == KmPostBadgeStatus.SELECTED ? Icons.KmPostSelected : Icons.KmPost;
    return (
        <div className={classes} onClick={onClick}>
            <Icon size={IconSize.SMALL} />
            <span>{kmPost.kmNumber}</span>
        </div>
    );
};
