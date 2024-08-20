import * as React from 'react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { compareTimestamps } from 'utils/date-utils';
import { PublicationList } from 'publication/card/publication-list';
import RatkoPublishButton from 'ratko/ratko-publish-button';
import { RatkoPushErrorDetails } from 'ratko/ratko-push-error';
import { ratkoPushFailed, ratkoPushSucceeded } from 'ratko/ratko-model';
import Card from 'geoviite-design-lib/card/card';
import styles from './publication-card.scss';
import { RatkoStatus } from 'ratko/ratko-api';
import i18n from 'i18next';
import { Link } from 'vayla-design-lib/link/link';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { useAppNavigate } from 'common/navigate';
import { defaultPublicationSearch } from 'publication/publication-utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { getLatestPublications } from 'publication/publication-api';
import { TimeStamp } from 'common/common-model';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { LayoutBranchType } from 'publication/publication-model';

type PublishListProps = {
    publicationChangeTime: TimeStamp;
    ratkoPushChangeTime: TimeStamp;
    splitChangeTime: TimeStamp;
    ratkoStatus: RatkoStatus | undefined;
    branchType: LayoutBranchType;
};

const parseRatkoConnectionError = (errorType: string, ratkoStatusCode: number, contact: string) => {
    return (
        <span>
            {i18n.t(`error-in-ratko-connection.${errorType}`, { code: ratkoStatusCode })}
            <br />
            {i18n.t(`error-in-ratko-connection.${contact}`)}
        </span>
    );
};

const parseRatkoOfflineStatus = (ratkoStatus: { statusCode: number }) => {
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

export const MAX_LISTED_PUBLICATIONS = 8;

const PublicationCard: React.FC<PublishListProps> = ({
    publicationChangeTime,
    ratkoPushChangeTime,
    splitChangeTime,
    ratkoStatus,
    branchType,
}) => {
    const { t } = useTranslation();
    const navigate = useAppNavigate();

    const trackLayoutActionDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    useEffect(() => {
        trackLayoutActionDelegates.clearPublicationSelection();
    }, []);

    const [pageCount, setPageCount] = React.useState(1);
    const [publications, publicationFetchStatus] = useLoaderWithStatus(
        () => getLatestPublications(MAX_LISTED_PUBLICATIONS * pageCount, branchType),
        [publicationChangeTime, ratkoPushChangeTime, splitChangeTime, pageCount],
    );

    const allPublications =
        publications
            ?.sort((i1, i2) => compareTimestamps(i1.publicationTime, i2.publicationTime))
            ?.reverse() ?? [];

    const nonSuccesses = allPublications.filter(
        (publication) => !ratkoPushSucceeded(publication.ratkoPushStatus),
    );

    const successes = allPublications.filter((publication) =>
        ratkoPushSucceeded(publication.ratkoPushStatus),
    );

    const latestFailures = allPublications.filter((publication) =>
        ratkoPushFailed(publication.ratkoPushStatus),
    );
    const ratkoConnectionError =
        ratkoStatus && !ratkoStatus.isOnline && ratkoStatus.statusCode >= 300;

    const allWaiting = nonSuccesses.every((publication) => !publication.ratkoPushStatus);

    const navigateToPublicationLog = () => {
        trackLayoutActionDelegates.setSelectedPublicationSearch(defaultPublicationSearch);
        navigate('publication-search');
    };

    return (
        <Card
            className={styles['publication-card']}
            content={
                <React.Fragment>
                    <h2 className={styles['publication-card__title']}>
                        {t(
                            branchType === 'MAIN'
                                ? 'publication-card.title'
                                : 'publication-card.designs-title',
                        )}
                    </h2>
                    <ProgressIndicatorWrapper
                        indicator={ProgressIndicatorType.Area}
                        inProgress={publicationFetchStatus !== LoaderStatus.Ready}>
                        {(nonSuccesses.length > 0 || ratkoConnectionError) && (
                            <section>
                                <h3 className={styles['publication-card__subsection-title']}>
                                    {t('publication-card.waiting')}
                                </h3>
                                {ratkoConnectionError && (
                                    <p className={styles['publication-card__title-errors']}>
                                        {parseRatkoOfflineStatus(ratkoStatus)}
                                    </p>
                                )}
                                {latestFailures.map((fail) => (
                                    <RatkoPushErrorDetails key={fail.id} failedPublication={fail} />
                                ))}
                                <PublicationList publications={nonSuccesses} />
                                {allWaiting && (
                                    <div className={styles['publication-card__waiting-text']}>
                                        <Icons.SetTime
                                            size={IconSize.SMALL}
                                            color={IconColor.INHERIT}
                                        />
                                        <span>{t('publication-card.transfer-starts-shortly')}</span>
                                    </div>
                                )}
                                {latestFailures.length > 0 && (
                                    <div className={styles['publication-card__ratko-push-button']}>
                                        <RatkoPublishButton
                                            branchType={branchType}
                                            disabled={ratkoConnectionError}
                                        />
                                    </div>
                                )}
                            </section>
                        )}
                        <section>
                            <h3 className={styles['publication-card__subsection-title']}>
                                {t('publication-card.latest')}
                            </h3>
                            <PublicationList publications={successes} />
                        </section>
                        {successes.length == 0 && nonSuccesses.length == 0 && (
                            <div className={styles['publication-card__no-publications']}>
                                {t('publication-card.no-success-publications')}
                            </div>
                        )}
                        <div>
                            <Link onClick={() => setPageCount(pageCount + 1)}>
                                {t('publication-card.show-more')}
                            </Link>
                        </div>
                        <br />
                        {branchType === 'MAIN' && (
                            <div>
                                <Link
                                    onClick={() => navigateToPublicationLog()}
                                    qa-id={'open-publication-log'}>
                                    {t('publication-card.log-link')}
                                </Link>
                            </div>
                        )}
                    </ProgressIndicatorWrapper>
                </React.Fragment>
            }
        />
    );
};

export default PublicationCard;
