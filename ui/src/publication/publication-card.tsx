import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { compareTimestamps } from 'utils/date-utils';
import {
    PublicationListingItem,
    ratkoPushFailed,
    RatkoPushStatus,
} from 'publication/publication-model';
import { PublicationList } from 'publication/publication-list';
import { ButtonSize } from 'vayla-design-lib/button/button';
import RatkoPublishButton from 'publication/ratko-publish-button';
import { RatkoPushErrorDetails } from 'publication/ratko-push-error';

type PublishListProps = {
    itemClicked: (pub: PublicationListingItem) => void;
    publications: PublicationListingItem[];
    anyFailed: boolean;
};

const PublicationCard: React.FC<PublishListProps> = ({ publications, itemClicked, anyFailed }) => {
    const { t } = useTranslation();
    const allPublications = publications
        .sort((i1, i2) => compareTimestamps(i1.publishTime, i2.publishTime))
        .reverse();

    const failures = allPublications.filter((publication) => ratkoPushFailed(publication.status));
    const successes = allPublications.filter((publication) => !ratkoPushFailed(publication.status));
    const latestFailureWithPushError = failures
        .filter((p) => p.status == RatkoPushStatus.FAILED && p.hasRatkoPushError)
        .at(-1);

    return (
        <React.Fragment>
            {failures.length > 0 && (
                <div>
                    <h3>{t('publishing.publish-issues')}</h3>
                    <div style={{ marginBottom: '12px' }}>
                        <RatkoPublishButton size={ButtonSize.SMALL} />
                    </div>
                    {latestFailureWithPushError && (
                        <RatkoPushErrorDetails latestFailure={latestFailureWithPushError} />
                    )}
                    <PublicationList
                        publications={failures}
                        publicationClicked={itemClicked}
                        anyFailed={anyFailed}
                    />
                </div>
            )}
            {successes.length > 0 && (
                <div>
                    <h3>{t('publication-card.latest')}</h3>
                    <PublicationList
                        publications={successes}
                        publicationClicked={itemClicked}
                        anyFailed={anyFailed}
                    />
                </div>
            )}
        </React.Fragment>
    );
};

export default PublicationCard;
