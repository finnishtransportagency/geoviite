import PublicationDetailsView from 'publication/publication-details-view';
import { useCommonDataAppSelector } from 'store/hooks';
import { useLoader } from 'utils/react-utils';
import { getPublication } from 'publication/publication-api';
import { useParams } from 'react-router-dom';
import React from 'react';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { PublicationId } from 'publication/publication-model';
import { LayoutContext } from 'common/common-model';

type PublicationDetailsContainerProps = {
    layoutContext: LayoutContext;
};

export const PublicationDetailsContainer: React.FC<PublicationDetailsContainerProps> = ({
    layoutContext,
}) => {
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
            layoutContext={layoutContext}
            publication={publication}
            setSelectedPublicationId={trackLayoutActionDelegates.setSelectedPublicationId}
            changeTime={publicationChangeTime}
        />
    );
};
