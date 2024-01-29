import React from 'react';
import { PublicationDetails } from 'publication/publication-model';
import { PublicationListRow } from 'publication/card/publication-list-row';
import { useAppNavigate } from 'common/navigate';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';

type PublicationListProps = {
    publications: PublicationDetails[];
};

export const PublicationList: React.FC<PublicationListProps> = ({ publications }) => {
    const navigate = useAppNavigate();

    const trackLayoutActionDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    return (
        <div qa-id="publication-list">
            {publications.map((publication) => (
                <PublicationListRow
                    key={publication.id}
                    publication={publication}
                    navigate={navigate}
                    setSelectedPublicationId={trackLayoutActionDelegates.setSelectedPublicationId}
                />
            ))}
        </div>
    );
};
