import { LayoutTrackNumberId, ReferenceLineId } from 'track-layout/track-layout-model';
import { useTrackNumber, useTrackNumberReferenceLine } from 'track-layout/track-layout-react-utils';
import { PublishType } from 'common/common-model';
import { Link } from 'vayla-design-lib/link/link';
import React from 'react';
import { useTrackLayoutAppDispatch } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
import { createEmptyItemCollections } from 'selection/selection-store';

export type TrackNumberLinkProps = {
    trackNumberId?: LayoutTrackNumberId;
    publishType: PublishType;
    onClick?: (
        trackNumberId: LayoutTrackNumberId,
        referenceLineId: ReferenceLineId | undefined,
    ) => void;
};

function createSelectAction() {
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);
    return (trackNumberId: LayoutTrackNumberId, referenceLineId: ReferenceLineId | undefined) =>
        delegates.onSelect({
            ...createEmptyItemCollections(),
            trackNumbers: [trackNumberId],
            referenceLines: referenceLineId ? [referenceLineId] : [],
        });
}

export const TrackNumberLink: React.FC<TrackNumberLinkProps> = (props: TrackNumberLinkProps) => {
    const trackNumber = useTrackNumber(props.publishType, props.trackNumberId);
    const referenceLine = useTrackNumberReferenceLine(props.trackNumberId, props.publishType);
    const clickAction = props.onClick || createSelectAction();
    return (
        <Link onClick={() => trackNumber && clickAction(trackNumber.id, referenceLine?.id)}>
            {trackNumber?.number || ''}
        </Link>
    );
};
