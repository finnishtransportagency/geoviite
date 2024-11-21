import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { useTrackNumberWithStatus } from 'track-layout/track-layout-react-utils';
import { LayoutContext, TimeStamp } from 'common/common-model';
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
    layoutContext: LayoutContext;
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

export const TrackNumberLinkContainer: React.FC<TrackNumberLinkContainerProps> = ({
    trackNumberId,
}) => {
    const layoutContext = useTrackLayoutAppSelector((state) => state.layoutContext);
    const changeTime = useCommonDataAppSelector((state) => state.changeTimes.layoutTrackNumber);

    return (
        <TrackNumberLink
            trackNumberId={trackNumberId}
            layoutContext={layoutContext}
            changeTime={changeTime}
        />
    );
};

export const TrackNumberLink: React.FC<TrackNumberLinkProps> = ({
    trackNumberId,
    layoutContext,
    changeTime,
    onClick,
}: TrackNumberLinkProps) => {
    const { t } = useTranslation();
    const [trackNumber, status] = useTrackNumberWithStatus(
        trackNumberId,
        layoutContext,
        changeTime,
    );

    const clickAction = onClick || createSelectAction();

    return status === LoaderStatus.Ready && trackNumber ? (
        <React.Fragment>
            <Link onClick={() => clickAction(trackNumber.id)}>{trackNumber.number}</Link>
            {trackNumber.state === 'DELETED' ? (
                <span>&nbsp;({t('enum.LayoutState.DELETED')})</span>
            ) : (
                ''
            )}
        </React.Fragment>
    ) : (
        <Spinner />
    );
};
