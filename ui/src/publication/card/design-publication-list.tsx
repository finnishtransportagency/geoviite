import React from 'react';
import { PublicationCause, PublicationDetails } from 'publication/publication-model';
import { DesignPublicationListRow } from 'publication/card/design-publication-list-row';
import { designBranch } from 'common/common-model';
import { LayoutDesign } from 'track-layout/layout-design-api';

type PublicationListProps = {
    publications: PublicationDetails[];
    designs: LayoutDesign[];
};

const HIDDEN_PUBLICATION_CAUSES: PublicationCause[] = [
    PublicationCause.LAYOUT_DESIGN_CANCELLATION,
    PublicationCause.CALCULATED_CHANGE,
] as const;

export const DesignPublicationList: React.FC<PublicationListProps> = ({
    publications,
    designs,
}) => {
    return (
        <div qa-id="publication-list">
            {publications
                .filter((p) => !HIDDEN_PUBLICATION_CAUSES.includes(p.cause))
                .map((publication) => {
                    const designName = designs?.find(
                        (d) => designBranch(d.id) === publication.layoutBranch.branch,
                    )?.name;

                    return (
                        <DesignPublicationListRow
                            key={publication.id}
                            publication={publication}
                            designName={designName}
                        />
                    );
                })}
        </div>
    );
};
