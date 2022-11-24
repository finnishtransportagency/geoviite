import React from 'react';
import styles from './publication-list.scss';
import { Link } from 'vayla-design-lib/link/link';
import { formatDateFull } from 'utils/date-utils';
import { ReferenceLineBadge } from 'geoviite-design-lib/alignment/reference-line-badge';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import {
    PublicationListingItem,
    ratkoPushFailed,
    ratkoPushInProgress,
} from 'publication/publication-model';
import { createClassName } from 'vayla-design-lib/utils';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { useTranslation } from 'react-i18next';

type PublicationListProps = {
    publications: PublicationListingItem[];
    publicationClicked: (publication: PublicationListingItem) => void;
    anyFailed: boolean;
};

const MAX_TRUNCATED_PUBLICATIONS_SHOWN = 8;

export const PublicationList: React.FC<PublicationListProps> = ({
    publications,
    publicationClicked,
    anyFailed,
}) => {
    const { t } = useTranslation();
    const canShowMore = publications.length > MAX_TRUNCATED_PUBLICATIONS_SHOWN;
    const [truncated, setTruncated] = React.useState(true);
    const [shownPublications, setShownPublications] = React.useState<PublicationListingItem[]>([]);
    const trackNumbers = useTrackNumbers('OFFICIAL');

    const truncateOrExpandPublications = () => {
        setTruncated(!truncated);
    };

    React.useEffect(() => {
        truncated
            ? setShownPublications(publications.slice(0, MAX_TRUNCATED_PUBLICATIONS_SHOWN))
            : setShownPublications(publications);
    }, [publications, truncated]);

    const className = createClassName(
        styles['publication-list__more'],
        !truncated && styles['publication-list__more--open'],
    );

    return (
        <React.Fragment>
            <div>
                {shownPublications.map((publication, publicationIndex) => {
                    const isWaitingAfterFailure = anyFailed && publication.status === null;

                    return (
                        <div className={styles['publication-list-item']} key={publicationIndex}>
                            {publication.status === null && (
                                <div
                                    className={styles['publication-list-item__status--waiting']}
                                    title={t('publishing.waiting')}
                                />
                            )}

                            {ratkoPushInProgress(publication.status) && (
                                <div
                                    className={styles['publication-list-item__status--in-progress']}
                                    title={t('publishing.in-progress')}
                                />
                            )}
                            <div className={styles['publication-list-item__text']}>
                                <Link onClick={() => publicationClicked(publication)}>
                                    {formatDateFull(publication.publishTime)}
                                </Link>
                            </div>
                            <div className={styles['publication-list-item__track-numbers']}>
                                {publication.trackNumberIds.map(
                                    (trackNumberId, trackNumberIndex) => {
                                        const trackNumber = trackNumbers?.find(
                                            (trackNumber) => trackNumber.id === trackNumberId,
                                        );
                                        return (
                                            trackNumber && (
                                                <span
                                                    key={publicationIndex + '_' + trackNumberIndex}>
                                                    <ReferenceLineBadge trackNumber={trackNumber} />
                                                </span>
                                            )
                                        );
                                    },
                                )}
                            </div>
                            {isWaitingAfterFailure && (
                                <React.Fragment>
                                    <div className={styles['publication-list-item__issue']}>
                                        <span
                                            className={
                                                styles['publication-list-item__issue--info']
                                            }>
                                            <Icons.Denied
                                                color={IconColor.INHERIT}
                                                size={IconSize.MEDIUMSMALL}
                                            />
                                        </span>
                                    </div>
                                    <div className={styles['publication-list-item__text']}>
                                        {' '}
                                        {t('publishing.not-published')}
                                    </div>
                                </React.Fragment>
                            )}
                            {ratkoPushFailed(publication.status) && (
                                <React.Fragment>
                                    <div className={styles['publication-list-item__issue']}>
                                        <span
                                            className={
                                                styles['publication-list-item__issue--error']
                                            }>
                                            <Icons.StatusError
                                                color={IconColor.INHERIT}
                                                size={IconSize.MEDIUMSMALL}
                                            />
                                        </span>
                                    </div>
                                    <div className={styles['publication-list-item__text']}>
                                        {' '}
                                        {t('publishing.publish-issue')}
                                    </div>
                                </React.Fragment>
                            )}
                        </div>
                    );
                })}
            </div>
            {canShowMore && (
                <React.Fragment>
                    <Link disabled={canShowMore} onClick={truncateOrExpandPublications}>
                        <span className={className}>
                            <Icons.Down size={IconSize.SMALL} color={IconColor.INHERIT} />
                        </span>
                        <span>
                            {truncated
                                ? t('publication-card.show-more')
                                : t('publication-card.show-less')}
                        </span>
                    </Link>
                </React.Fragment>
            )}
        </React.Fragment>
    );
};
