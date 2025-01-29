import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import styles from './km-post-badge.scss';
import { LayoutKmPost, LayoutTrackNumber } from 'track-layout/track-layout-model';
import { createClassName } from 'vayla-design-lib/utils';

type KmPostBadgeProps = {
    kmPost: LayoutKmPost;
    trackNumber?: LayoutTrackNumber;
    showTrackNumberInBadge?: boolean;
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

const statusToIcon = (status: KmPostBadgeStatus) => {
    if (status === KmPostBadgeStatus.DISABLED) return Icons.KmPostDisabled;
    else if (status === KmPostBadgeStatus.SELECTED) return Icons.KmPostSelected;
    else return Icons.KmPost;
};

export const KmPostBadge: React.FC<KmPostBadgeProps> = ({
    kmPost,
    trackNumber,
    status = KmPostBadgeStatus.DEFAULT,
    showTrackNumberInBadge,
    onClick,
}: KmPostBadgeProps) => {
    const { t } = useTranslation();
    const classes = createClassName(
        styles['km-post-badge'],
        status,
        onClick && styles['km-post-badge--clickable'],
    );

    const Icon = statusToIcon(status);
    const onTrackNumberTranslation = t('tool-panel.km-post.geometry.linking.track-number', {
        trackNumber: trackNumber?.number,
    });
    return (
        <span
            className={classes}
            title={trackNumber ? onTrackNumberTranslation : ''}
            onClick={onClick}>
            <Icon size={IconSize.SMALL} />
            <span>{`${kmPost.kmNumber} ${
                showTrackNumberInBadge && trackNumber ? `/ ${onTrackNumberTranslation}` : ''
            }`}</span>
        </span>
    );
};
