import * as React from 'react';
import styles from './alignment-badge.scss';
import { LayoutTrackNumber, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createClassName } from 'vayla-design-lib/utils';
import { useTrackNumberWithStatus } from 'track-layout/track-layout-react-utils';
import { createDelegates } from 'store/store-utils';
import { LoaderStatus } from 'utils/react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { LayoutContext, TimeStamp } from 'common/common-model';

type TrackNumberBadgeLinkProps = {
    trackNumberId: LayoutTrackNumberId;
    layoutContext: LayoutContext;
    changeTime: TimeStamp;
    onClick?: (trackNumberId: LayoutTrackNumberId) => void;
};

type TrackNumberBadgeProps = {
    trackNumber: LayoutTrackNumber;
    onClick?: React.MouseEventHandler;
    status?: TrackNumberBadgeStatus;
};

export enum TrackNumberBadgeStatus {
    DEFAULT = 'alignment-badge--default',
    LINKED = 'alignment-badge--linked',
    UNLINKED = 'alignment-badge--unlinked',
    SELECTED = 'alignment-badge--selected',
    DISABLED = 'alignment-badge--disabled',
}

export const TrackNumberBadgeLink: React.FC<TrackNumberBadgeLinkProps> = ({
    trackNumberId,
    layoutContext,
    changeTime,
    onClick,
}: TrackNumberBadgeLinkProps) => {
    const [trackNumber, status] = useTrackNumberWithStatus(
        trackNumberId,
        layoutContext,
        changeTime,
    );

    const clickAction = React.useCallback(() => {
        if (onClick) {
            onClick(trackNumberId);
        } else {
            const delegates = createDelegates(TrackLayoutActions);
            delegates.onSelect({ trackNumbers: [trackNumberId] });
            delegates.setToolPanelTab({
                id: trackNumberId,
                type: 'TRACK_NUMBER',
            });
        }
    }, [onClick, trackNumberId]);

    return status === LoaderStatus.Ready && trackNumber ? (
        <TrackNumberBadge trackNumber={trackNumber} onClick={clickAction} />
    ) : (
        <Spinner />
    );
};

export const TrackNumberBadge: React.FC<TrackNumberBadgeProps> = ({
    trackNumber,
    onClick,
    status = TrackNumberBadgeStatus.DEFAULT,
}: TrackNumberBadgeProps) => {
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
