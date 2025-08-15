import React from 'react';
import { PublicationCause, PublicationDetails } from 'publication/publication-model';
import { PublicationListRow } from 'publication/card/publication-list-row';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';

type PublicationListProps = {
    publications: PublicationDetails[];
};

const HIDDEN_PUBLICATION_CAUSES = [
    PublicationCause.LAYOUT_DESIGN_CANCELLATION,
    PublicationCause.CALCULATED_CHANGE,
];

export const PublicationList: React.FC<PublicationListProps> = ({ publications }) => {
    const trackLayoutActionDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    return (
        <div qa-id="publication-list">
            {publications
                .filter((p) => !HIDDEN_PUBLICATION_CAUSES.includes(p.cause))
                .map((publication) => (
                    <PublicationListRow
                        key={publication.id}
                        publication={publication}
                        setSelectedPublicationId={
                            trackLayoutActionDelegates.setSelectedPublicationId
                        }
                    />
                ))}
        </div>
    );
};
