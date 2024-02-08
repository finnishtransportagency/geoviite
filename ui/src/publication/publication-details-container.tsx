import PublicationDetailsView from 'publication/publication';
import { useCommonDataAppSelector } from 'store/hooks';
import { useLoader } from 'utils/react-utils';
import { getPublication } from 'publication/publication-api';
import { useParams } from 'react-router-dom';
import { PublicationId } from 'preview/preview-table';
import React from 'react';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';

export const PublicationDetailsContainer: React.FC = () => {
    const trackLayoutActionDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    const selectedPublicationId: PublicationId | undefined = useParams().publicationId;
    const publicationChangeTime = useCommonDataAppSelector(
        (state) => state.changeTimes.publication,
    );

    const publication = useLoader(
        () => (selectedPublicationId ? getPublication(selectedPublicationId) : undefined),
        [selectedPublicationId, publicationChangeTime],
    );

    if (!selectedPublicationId || !publication) {
        return <React.Fragment />;
    }

    return (
        <PublicationDetailsView
            publication={publication}
            setSelectedPublicationId={trackLayoutActionDelegates.setSelectedPublicationId}
            changeTime={publicationChangeTime}
        />
    );
};
