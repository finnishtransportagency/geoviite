import * as React from 'react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { compareTimestamps } from 'utils/date-utils';
import { PublicationList } from 'publication/card/publication-list';
import RatkoPublishButton from 'ratko/ratko-publish-button';
import { RatkoPushErrorDetails } from 'ratko/ratko-push-error';
import { ratkoPushFailed, RatkoPushStatus, ratkoPushSucceeded } from 'ratko/ratko-model';
import Card from 'geoviite-design-lib/card/card';
import styles from './publication-card.scss';
import { RatkoStatus } from 'ratko/ratko-api';
import i18n from 'i18next';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { useAppNavigate } from 'common/navigate';
import { defaultPublicationSearch } from 'publication/publication-utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { getLatestPublications } from 'publication/publication-api';
import { LayoutBranch, TimeStamp } from 'common/common-model';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { LayoutBranchType, PublicationDetails } from 'publication/publication-model';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

type PublishListProps = {
    publicationChangeTime: TimeStamp;
    ratkoPushChangeTime: TimeStamp;
    splitChangeTime: TimeStamp;
    ratkoStatus: RatkoStatus | undefined;
    branchType: LayoutBranchType;
};

const RATKO_SUPPORT_EMAIL = 'vayla.asiakkaat.fi@cgi.com';
export const GEOVIITE_SUPPORT_EMAIL = 'geoviite.support@solita.fi';

const parseRatkoConnectionError = (
    errorType: string,
    ratkoStatusCode: number | undefined,
    contact: string,
) => {
    return (
        <span>
            {i18n.t(`error-in-ratko-connection.${errorType}`, { code: ratkoStatusCode })}
            <br />
            {i18n.t(`error-in-ratko-connection.${contact}`, {
                geoviiteSupportEmail: GEOVIITE_SUPPORT_EMAIL,
                ratkoSupportEmail: RATKO_SUPPORT_EMAIL,
            })}
        </span>
    );
};

const parseRatkoOfflineStatus = (ratkoStatus: number | undefined): React.JSX.Element => {
    if (ratkoStatus === undefined) {
        return parseRatkoConnectionError(
            'connection-error-without-status-code',
            ratkoStatus,
            'contact-geoviite-support-if-needed',
        );
    } else if (ratkoStatus >= 500) {
        return ratkoStatus === 503
            ? parseRatkoConnectionError(
                  'temporary-error-status-code',
                  ratkoStatus,
                  'contact-ratko-support-if-needed',
              )
            : parseRatkoConnectionError(
                  'connection-error-status-code',
                  ratkoStatus,
                  'contact-ratko-support',
              );
    } else if (ratkoStatus >= 400) {
        return parseRatkoConnectionError(
            'connection-error-status-code',
            ratkoStatus,
            'contact-geoviite-support',
        );
    } else if (ratkoStatus >= 300) {
        return parseRatkoConnectionError(
            'integration-error-status-code',
            ratkoStatus,
            'contact-geoviite-support',
        );
    } else {
        return parseRatkoConnectionError(
            'connection-error-without-status-code',
            ratkoStatus,
            'contact-geoviite-support-if-needed',
        );
    }
};

function latestFailureByLayoutBranch(
    allPublicationForBranchType: PublicationDetails[],
): PublicationDetails[] {
    const failures = allPublicationForBranchType.filter((publication) =>
        ratkoPushFailed(publication.ratkoPushStatus),
    );
    // Design publications are independent from each other, but only per design branch, so only show the latest
    // failure per design branch. Or if this is the main branch's publication card, only the latest one for that.
    return [
        ...failures
            .reduce((mapByPublication, publication) => {
                if (!mapByPublication.has(publication.layoutBranch.branch)) {
                    mapByPublication.set(publication.layoutBranch.branch, publication);
                }
                return mapByPublication;
            }, new Map<LayoutBranch, PublicationDetails>())
            .values(),
    ].sort((a, b) => compareTimestamps(b.publicationTime, a.publicationTime));
}

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
    const reachedLastPublication =
        !publications || publications?.items?.length === publications?.totalCount;

    const allPublications =
        publications?.items
            ?.sort((i1, i2) => compareTimestamps(i1.publicationTime, i2.publicationTime))
            ?.reverse() ?? [];

    const nonSuccesses = allPublications.filter(
        (publication) => !ratkoPushSucceeded(publication.ratkoPushStatus),
    );

    const successes = allPublications.filter((publication) =>
        ratkoPushSucceeded(publication.ratkoPushStatus),
    );

    const latestFailures = latestFailureByLayoutBranch(allPublications);
    const ratkoConnectionError =
        ratkoStatus &&
        (ratkoStatus.connectionStatus === 'ONLINE_ERROR' ||
            ratkoStatus.connectionStatus === 'OFFLINE');

    const allWaiting =
        nonSuccesses.length > 0 &&
        nonSuccesses.every(
            (publication) =>
                !publication.ratkoPushStatus ||
                publication.ratkoPushStatus === RatkoPushStatus.MANUAL_RETRY,
        );

    const navigateToPublicationLog = () => {
        trackLayoutActionDelegates.setSelectedPublicationSearch(defaultPublicationSearch);
        navigate('publication-search');
    };

    const latestPublicationsTitle =
        branchType === 'MAIN' ? t('publication-card.latest') : t('publication-card.designs-latest');

    const noPublicationsMessage =
        branchType === 'MAIN'
            ? t('publication-card.no-publications')
            : t('publication-card.designs-no-publications');

    // All design publications immediately succeed for now as they are not transferred anywhere.
    const successfulPublicationsToDisplay = branchType === 'MAIN' ? successes : allPublications;

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
                        {ratkoConnectionError && (
                            <p className={styles['publication-card__title-errors']}>
                                {parseRatkoOfflineStatus(ratkoStatus?.ratkoStatusCode)}
                            </p>
                        )}
                        {branchType === 'MAIN' && nonSuccesses.length > 0 && (
                            <section>
                                <h3 className={styles['publication-card__subsection-title']}>
                                    {t('publication-card.waiting')}
                                </h3>
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
                                        <span>
                                            {ratkoConnectionError
                                                ? t(
                                                      'publication-card.transfer-starts-after-reconnect',
                                                  )
                                                : t('publication-card.transfer-starts-shortly')}
                                        </span>
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
                        {(successfulPublicationsToDisplay.length > 0 || reachedLastPublication) && (
                            <section>
                                <h3 className={styles['publication-card__subsection-title']}>
                                    {latestPublicationsTitle}
                                </h3>
                                <PublicationList publications={successfulPublicationsToDisplay} />
                            </section>
                        )}
                        {successfulPublicationsToDisplay.length === 0 &&
                            (nonSuccesses.length === 0 || reachedLastPublication) && (
                                <div className={styles['publication-card__no-publications']}>
                                    {noPublicationsMessage}
                                </div>
                            )}
                        {!reachedLastPublication && (
                            <div className={styles['publication-card__show-more']}>
                                <AnchorLink onClick={() => setPageCount(pageCount + 1)}>
                                    {t('publication-card.show-more')}
                                </AnchorLink>
                            </div>
                        )}
                        <br />
                        {branchType === 'MAIN' && (
                            <div>
                                <AnchorLink
                                    onClick={() => navigateToPublicationLog()}
                                    qa-id={'open-publication-log'}>
                                    {t('publication-card.log-link')}
                                </AnchorLink>
                            </div>
                        )}
                    </ProgressIndicatorWrapper>
                </React.Fragment>
            }
        />
    );
};

export default PublicationCard;
