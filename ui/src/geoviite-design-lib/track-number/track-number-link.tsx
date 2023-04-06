import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { useTrackNumber } from 'track-layout/track-layout-react-utils';
import { PublishType } from 'common/common-model';
import { Link } from 'vayla-design-lib/link/link';
import React from 'react';
import { useAppDispatch } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createEmptyItemCollections } from 'selection/selection-store';

export type TrackNumberLinkProps = {
    trackNumberId?: LayoutTrackNumberId;
    publishType: PublishType;
    onClick?: (trackNumberId: LayoutTrackNumberId) => void;
};

function createSelectAction() {
    const dispatch = useAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);
    return (trackNumberId: LayoutTrackNumberId) =>
        delegates.onSelect({
            ...createEmptyItemCollections(),
            trackNumbers: [trackNumberId],
        });
}

export const TrackNumberLink: React.FC<TrackNumberLinkProps> = (props: TrackNumberLinkProps) => {
    const trackNumber = useTrackNumber(props.publishType, props.trackNumberId);
    const clickAction = props.onClick || createSelectAction();
    return (
        <Link onClick={() => trackNumber && clickAction(trackNumber.id)}>
            {trackNumber?.number || ''}
        </Link>
    );
};
