import React from 'react';
import { PublicationDetails } from 'publication/publication-model';
import { PublicationRow } from 'publication/card/publication-list-row';

type PublicationListProps = {
    publications: PublicationDetails[];
};

export const PublicationList: React.FC<PublicationListProps> = ({ publications }) => {
    return (
        <div qa-id="publication-list">
            {publications.map((publication) => (
                <PublicationRow key={publication.id} publication={publication} />
            ))}
        </div>
    );
};
