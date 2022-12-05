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
import { RatkoStatus } from 'ratko/ratko-api';

type PublishListProps = {
    itemClicked: (pub: PublicationListingItem) => void;
    publications: PublicationListingItem[];
    anyFailed: boolean;
    ratkoStatus: RatkoStatus | undefined;
};

const PublicationCard: React.FC<PublishListProps> = ({publications, itemClicked, anyFailed, ratkoStatus}) => {
    const {t} = useTranslation();
    const allPublications = publications
        .sort((i1, i2) => compareTimestamps(i1.publishTime, i2.publishTime))
        .reverse();

    const failures = allPublications.filter((publication) => ratkoPushFailed(publication.status));
    const successes = allPublications.filter((publication) => !ratkoPushFailed(publication.status));
    const latestFailureWithPushError = failures
        .filter((p) => p.status == RatkoPushStatus.FAILED && p.hasRatkoPushError)
        .at(-1);


    const parseRatkoConnectionError = (errorType: string, ratkoStatusCode: number, contact: string) => {
        return (
            <ul className={styles['publication-card__ratko-connection-error']}>
                <li>{t(`error-in-ratko-connection.${errorType}`, [ratkoStatusCode])}</li>
                <li>{t(`error-in-ratko-connection.${contact}`)}</li>
            </ul>);
    };

    const parseRatkoStatus = (ratkoStatus: RatkoStatus) => {
        if (ratkoStatus.statusCode >= 400 && ratkoStatus.statusCode <= 403) {
            return parseRatkoConnectionError('connection-error-status-code', ratkoStatus.statusCode, 'contact-geoviite-support');
        } else if (ratkoStatus.statusCode >= 300 && ratkoStatus.statusCode <= 308) {
            return parseRatkoConnectionError('integration-error-status-code', ratkoStatus.statusCode, 'contact-geoviite-support');
        } else if (ratkoStatus.statusCode >= 500 && ratkoStatus.statusCode <= 511) {
            if (ratkoStatus.statusCode === 503) {
                return parseRatkoConnectionError('temporary-error-status-code', ratkoStatus.statusCode, 'contact-ratko-support-if-needed')
            }
            return parseRatkoConnectionError('connection-error-status-code', ratkoStatus.statusCode, 'contact-ratko-support');
        }
    };

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
                            <h3 className={styles['publication-card__subsection-title']}>
                                {t('publishing.publish-issues')}
                            </h3>
                            {ratkoStatus &&
                                <p className={styles['publication-card__title-errors']}>
                                    {parseRatkoStatus(ratkoStatus)}
                                </p>
                            }
                            <div className={styles['publication-card__ratko-push-button']}>
                                <RatkoPublishButton size={ButtonSize.SMALL}/>
                            </div>
                            {latestFailureWithPushError && (
                                <RatkoPushErrorDetails latestFailure={latestFailureWithPushError}/>
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
                            <h3 className={styles['publication-card__subsection-title']}>
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
