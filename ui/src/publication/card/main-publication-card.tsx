import * as React from 'react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { compareTimestamps } from 'utils/date-utils';
import { MainPublicationList } from 'publication/card/main-publication-list';
import RatkoPublishButton from 'ratko/ratko-publish-button';
import { RatkoPushErrorDetails } from 'ratko/ratko-push-error';
import { ratkoPushFailed, RatkoPushStatus, ratkoPushSucceeded } from 'ratko/ratko-model';
import styles from './publication-card.scss';
import { RatkoStatus } from 'ratko/ratko-api';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { useAppNavigate } from 'common/navigate';
import { defaultPublicationSearch } from 'publication/publication-utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { getLatestPublications } from 'publication/publication-api';
import { LayoutBranch, TimeStamp } from 'common/common-model';
import { PublicationDetails } from 'publication/publication-model';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import PublicationCard, {
    NoPublicationsInfo,
    PUBLICATION_LIST_PAGE_SIZE,
    PublicationCardSection,
    ShowMorePublicationsLink,
} from 'publication/card/publication-card';

type MainPublicationCardProps = {
    publicationChangeTime: TimeStamp;
    ratkoPushChangeTime: TimeStamp;
    splitChangeTime: TimeStamp;
    ratkoStatus: RatkoStatus | undefined;
};

const RATKO_SUPPORT_EMAIL = 'vayla.asiakkaat.fi@cgi.com';
export const GEOVIITE_SUPPORT_EMAIL = 'geoviite.support@solita.fi';

type RatkoConnectionErrorProps = {
    errorType: string;
    ratkoStatusCode: number | undefined;
    contact: string;
};

const ratkoErrorContactInfo = (statusCode: number | undefined): string => {
    if (statusCode === undefined) {
        return 'contact-geoviite-support-if-needed';
    } else if (statusCode === 503) {
        return 'contact-ratko-support-if-needed';
    } else if (statusCode >= 500) {
        return 'contact-ratko-support';
    } else if (statusCode >= 400) {
        return 'contact-geoviite-support';
    } else if (statusCode >= 300) {
        return 'contact-geoviite-support';
    } else {
        return 'contact-geoviite-support-if-needed';
    }
};

const ratkoErrorType = (statusCode: number | undefined): string => {
    if (statusCode === undefined) {
        return 'connection-error-without-status-code';
    } else if (statusCode === 503) {
        return 'temporary-error-status-code';
    } else if (statusCode >= 500) {
        return 'connection-error-status-code';
    } else if (statusCode >= 400) {
        return 'connection-error-status-code';
    } else if (statusCode >= 300) {
        return 'integration-error-status-code';
    } else {
        return 'connection-error-without-status-code';
    }
};

const RatkoConnectionError: React.FC<RatkoConnectionErrorProps> = ({
    errorType,
    ratkoStatusCode,
    contact,
}) => {
    const { t } = useTranslation();
    return (
        <span>
            {t(`error-in-ratko-connection.${errorType}`, { code: ratkoStatusCode })}
            <br />
            {t(`error-in-ratko-connection.${contact}`, {
                geoviiteSupportEmail: GEOVIITE_SUPPORT_EMAIL,
                ratkoSupportEmail: RATKO_SUPPORT_EMAIL,
            })}
        </span>
    );
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

const WaitingRatkoPushReason: React.FC<{ hasConnectionError: boolean }> = ({
    hasConnectionError,
}) => {
    const { t } = useTranslation();
    return (
        <div className={styles['publication-card__waiting-text']}>
            <Icons.SetTime size={IconSize.SMALL} color={IconColor.INHERIT} />
            <span>
                {hasConnectionError
                    ? t('publication-card.transfer-starts-after-reconnect')
                    : t('publication-card.transfer-starts-shortly')}
            </span>
        </div>
    );
};

const MainPublicationCard: React.FC<MainPublicationCardProps> = ({
    publicationChangeTime,
    ratkoPushChangeTime,
    splitChangeTime,
    ratkoStatus,
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
        () => getLatestPublications(PUBLICATION_LIST_PAGE_SIZE * pageCount, 'MAIN'),
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
    const hasRatkoConnectionError =
        !!ratkoStatus &&
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

    return (
        <PublicationCard
            title={t('publication-card.title')}
            loading={publicationFetchStatus !== LoaderStatus.Ready}>
            <React.Fragment>
                {hasRatkoConnectionError && (
                    <p className={styles['publication-card__title-errors']}>
                        <RatkoConnectionError
                            ratkoStatusCode={ratkoStatus?.ratkoStatusCode}
                            errorType={ratkoErrorType(ratkoStatus?.ratkoStatusCode)}
                            contact={ratkoErrorContactInfo(ratkoStatus?.ratkoStatusCode)}
                        />
                    </p>
                )}
                {nonSuccesses.length > 0 && (
                    <PublicationCardSection title={t('publication-card.waiting')}>
                        {latestFailures.map((fail) => (
                            <RatkoPushErrorDetails key={fail.id} failedPublication={fail} />
                        ))}
                        <MainPublicationList publications={nonSuccesses} />
                        {allWaiting && (
                            <WaitingRatkoPushReason hasConnectionError={hasRatkoConnectionError} />
                        )}
                        {latestFailures.length > 0 && (
                            <div className={styles['publication-card__ratko-push-button']}>
                                <RatkoPublishButton
                                    branchType={'MAIN'}
                                    disabled={hasRatkoConnectionError}
                                />
                            </div>
                        )}
                    </PublicationCardSection>
                )}
                {(successes.length > 0 || reachedLastPublication) && (
                    <PublicationCardSection title={t('publication-card.latest')}>
                        <MainPublicationList publications={successes} />
                    </PublicationCardSection>
                )}
                {successes.length === 0 &&
                    (nonSuccesses.length === 0 || reachedLastPublication) && (
                        <NoPublicationsInfo title={t('publication-card.no-publications')} />
                    )}
                {!reachedLastPublication && (
                    <ShowMorePublicationsLink showMore={() => setPageCount(pageCount + 1)} />
                )}
                <div className={styles['publication-card__publication-log-link']}>
                    <AnchorLink
                        onClick={() => navigateToPublicationLog()}
                        qa-id={'open-publication-log'}>
                        {t('publication-card.log-link')}
                    </AnchorLink>
                </div>
            </React.Fragment>
        </PublicationCard>
    );
};

export default MainPublicationCard;
