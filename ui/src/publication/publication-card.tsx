import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { compareTimestamps } from 'utils/date-utils';
import { PublicationListingItem } from 'publication/publication-model';
import { PublicationList } from 'publication/publication-list';
import { ButtonSize } from 'vayla-design-lib/button/button';
import RatkoPublishButton from 'ratko/ratko-publish-button';
import { RatkoPushErrorDetails } from 'ratko/ratko-push-error';
import { ratkoPushFailed, RatkoPushStatus } from 'ratko/ratko-model';
import Card from 'card/card';
import styles from './publication-card.scss';

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
        <Card
            className={styles['publication-card']}
            content={
                <React.Fragment>
                    <h2 className={styles['publication-card__title']}>
                        {t('publication-card.title')}
                    </h2>
                    {failures.length > 0 && (
                        <React.Fragment>
                            <h3 className={styles['publication-card__issues-title']}>
                                {t('publishing.publish-issues')}
                            </h3>
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
                        </React.Fragment>
                    )}
                    {successes.length > 0 && (
                        <React.Fragment>
                            <h3 className={styles['publication-card__latest-title']}>
                                {t('publication-card.latest')}
                            </h3>
                            <PublicationList
                                publications={successes}
                                publicationClicked={itemClicked}
                                anyFailed={anyFailed}
                            />
                        </React.Fragment>
                    )}
                </React.Fragment>
            }></Card>
    );
};

export default PublicationCard;
