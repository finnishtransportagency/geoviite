import React from 'react';
import { PublicationDetails } from 'publication/publication-model';
import { MainPublicationListRow } from 'publication/card/main-publication-list-row';

type PublicationListProps = {
    publications: PublicationDetails[];
};

export const MainPublicationList: React.FC<PublicationListProps> = ({ publications }) => {
    return (
        <div qa-id="publication-list">
            {publications.map((publication) => (
                <MainPublicationListRow key={publication.id} publication={publication} />
            ))}
        </div>
    );
};
