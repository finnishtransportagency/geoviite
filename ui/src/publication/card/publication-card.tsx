import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { compareTimestamps } from 'utils/date-utils';
import { PublicationDetails } from 'publication/publication-model';
import { PublicationList } from 'publication/card/publication-list';
import { ButtonSize } from 'vayla-design-lib/button/button';
import RatkoPublishButton from 'ratko/ratko-publish-button';
import { RatkoPushErrorDetails } from 'ratko/ratko-push-error';
import { ratkoPushFailed } from 'ratko/ratko-model';
import Card from 'geoviite-design-lib/card/card';
import styles from './publication-card.scss';
import { RatkoStatus } from 'ratko/ratko-api';
import { EnvRestricted } from 'environment/env-restricted';
import i18n from 'i18next';
import { Link } from 'vayla-design-lib/link/link';

type PublishListProps = {
    itemClicked: (pub: PublicationDetails) => void;
    onShowPublicationLog: () => void;
    publications: PublicationDetails[];
    ratkoStatus: RatkoStatus | undefined;
};

const parseRatkoConnectionError = (errorType: string, ratkoStatusCode: number, contact: string) => {
    return (
        <span>
            {i18n.t(`error-in-ratko-connection.${errorType}`, [ratkoStatusCode])}
            <br />
            {i18n.t(`error-in-ratko-connection.${contact}`)}
        </span>
    );
};

const parseRatkoStatus = (ratkoStatus: RatkoStatus) => {
    if (ratkoStatus.statusCode >= 500) {
        return ratkoStatus.statusCode === 503
            ? parseRatkoConnectionError(
                  'temporary-error-status-code',
                  ratkoStatus.statusCode,
                  'contact-ratko-support-if-needed',
              )
            : parseRatkoConnectionError(
                  'connection-error-status-code',
                  ratkoStatus.statusCode,
                  'contact-ratko-support',
              );
    } else if (ratkoStatus.statusCode >= 400) {
        return parseRatkoConnectionError(
            'connection-error-status-code',
            ratkoStatus.statusCode,
            'contact-geoviite-support',
        );
    } else if (ratkoStatus.statusCode >= 300) {
        return parseRatkoConnectionError(
            'integration-error-status-code',
            ratkoStatus.statusCode,
            'contact-geoviite-support',
        );
    }
};

const MAX_SUCCESS_PUBLICATIONS = 8;

const PublicationCard: React.FC<PublishListProps> = ({
    publications,
    itemClicked,
    ratkoStatus,
    onShowPublicationLog,
}) => {
    const { t } = useTranslation();
    const allPublications = publications.sort(
        (i1, i2) => -compareTimestamps(i1.publicationTime, i2.publicationTime),
    );

    const failures = allPublications.filter((publication) =>
        ratkoPushFailed(publication.ratkoPushStatus),
    );

    const successes = allPublications
        .filter((publication) => !ratkoPushFailed(publication.ratkoPushStatus))
        .slice(0, MAX_SUCCESS_PUBLICATIONS);

    const ratkoConnectionError = ratkoStatus && ratkoStatus.statusCode >= 300;

    return (
        <Card
            className={styles['publication-card']}
            content={
                <React.Fragment>
                    <h2 className={styles['publication-card__title']}>
                        {t('publication-card.title')}
                    </h2>
                    {(failures.length > 0 || ratkoConnectionError) && (
                        <section>
                            <h3 className={styles['publication-card__subsection-title']}>
                                {t('publication-card.publish-issues')}
                            </h3>
                            {ratkoConnectionError && (
                                <p className={styles['publication-card__title-errors']}>
                                    {parseRatkoStatus(ratkoStatus)}
                                </p>
                            )}
                            {failures.length > 0 && (
                                <React.Fragment>
                                    <RatkoPushErrorDetails latestFailure={failures[0]} />
                                    <div className={styles['publication-card__ratko-push-button']}>
                                        <RatkoPublishButton
                                            size={ButtonSize.SMALL}
                                            disabled={ratkoConnectionError}
                                        />
                                    </div>
                                    <PublicationList
                                        publications={failures}
                                        publicationClicked={itemClicked}
                                        anyFailed={failures.length > 0}
                                    />
                                </React.Fragment>
                            )}
                        </section>
                    )}
                    <section>
                        <h3 className={styles['publication-card__subsection-title']}>
                            {t('publication-card.latest')}
                        </h3>
                        <PublicationList
                            publications={successes}
                            publicationClicked={itemClicked}
                        />
                    </section>

                    {successes.length == 0 && failures.length == 0 && (
                        <div className={styles['publication-card__no-publications']}>
                            {t('publication-card.no-success-publications')}
                        </div>
                    )}
                    <EnvRestricted restrictTo="dev">
                        <Link onClick={onShowPublicationLog}>{t('publication-card.log-link')}</Link>
                    </EnvRestricted>
                </React.Fragment>
            }
        />
    );
};

export default PublicationCard;
