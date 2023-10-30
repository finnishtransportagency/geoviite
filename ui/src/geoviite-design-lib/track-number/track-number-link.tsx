import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { useTrackNumberWithStatus } from 'track-layout/track-layout-react-utils';
import { PublishType, TimeStamp } from 'common/common-model';
import { Link } from 'vayla-design-lib/link/link';
import React from 'react';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createEmptyItemCollections } from 'selection/selection-store';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { LoaderStatus } from 'utils/react-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { useTranslation } from 'react-i18next';

export type TrackNumberLinkContainerProps = {
    trackNumberId?: LayoutTrackNumberId;
};

export type TrackNumberLinkProps = {
    trackNumberId?: LayoutTrackNumberId;
    publicationType: PublishType;
    changeTime: TimeStamp;
    onClick?: (trackNumberId: LayoutTrackNumberId) => void;
};

function createSelectAction() {
    const delegates = createDelegates(TrackLayoutActions);
    return (trackNumberId: LayoutTrackNumberId) =>
        delegates.onSelect({
            ...createEmptyItemCollections(),
            trackNumbers: [trackNumberId],
        });
}

export const TrackNumberLinkContainer: React.FC<TrackNumberLinkContainerProps> = (
    props: TrackNumberLinkContainerProps,
) => {
    const publicationType = useTrackLayoutAppSelector((state) => state.publishType);
    const changeTime = useCommonDataAppSelector((state) => state.changeTimes.layoutTrackNumber);
    return (
        <TrackNumberLink
            trackNumberId={props.trackNumberId}
            publicationType={publicationType}
            changeTime={changeTime}
        />
    );
};

export const TrackNumberLink: React.FC<TrackNumberLinkProps> = ({
    trackNumberId,
    publicationType,
    changeTime,
    onClick,
}: TrackNumberLinkProps) => {
    const { t } = useTranslation();
    const [trackNumber, status] = useTrackNumberWithStatus(
        publicationType,
        trackNumberId,
        changeTime,
    );
    const clickAction = onClick || createSelectAction();
    const name = trackNumber?.number || '';
    return status === LoaderStatus.Ready ? (
        <React.Fragment>
            <Link onClick={() => trackNumber && clickAction(trackNumber.id)}>{name}</Link>
            {trackNumber?.state === 'DELETED' ? (
                <span>&nbsp;({t('enum.layout-state.DELETED')})</span>
            ) : (
                ''
            )}
        </React.Fragment>
    ) : (
        <Spinner />
    );
};
