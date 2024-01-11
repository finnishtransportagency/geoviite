import PublicationDetailsView from 'publication/publication';
import { useCommonDataAppSelector } from 'store/hooks';
import { useLoaderWithStatus } from 'utils/react-utils';
import { getLatestPublications } from 'publication/publication-api';
import { MAX_LISTED_PUBLICATIONS } from 'publication/card/publication-card';
import { useParams } from 'react-router-dom';
import { PublicationId } from 'preview/preview-table';
import React from 'react';
import { ratkoPushFailed } from 'ratko/ratko-model';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';

export const PublicationDetailsContainer: React.FC = () => {
    const trackLayoutActionDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    const selectedPublicationId: PublicationId | undefined = useParams().publicationId;

    const ratkoPushChangeTime = useCommonDataAppSelector((state) => state.changeTimes.ratkoPush);
    const publicationChangeTime = useCommonDataAppSelector(
        (state) => state.changeTimes.publication,
    );

    const [publications, _publicationFetchStatus] = useLoaderWithStatus(
        () => getLatestPublications(MAX_LISTED_PUBLICATIONS).then((result) => result?.items),
        [publicationChangeTime, ratkoPushChangeTime],
    );

    const publication = publications?.find((p) => p.id == selectedPublicationId);
    const anyFailed = !!publications?.some((p) => ratkoPushFailed(p.ratkoPushStatus));

    if (!selectedPublicationId || !publication) {
        return <React.Fragment />;
    }

    return (
        <PublicationDetailsView
            publication={publication}
            setSelectedPublicationId={trackLayoutActionDelegates.setSelectedPublicationId}
            anyFailed={anyFailed}
            changeTime={publicationChangeTime}
        />
    );
};
