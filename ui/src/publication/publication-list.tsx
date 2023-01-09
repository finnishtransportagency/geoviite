import React from 'react';
import styles from './publication-list.scss';
import { Link } from 'vayla-design-lib/link/link';
import { formatDateFull } from 'utils/date-utils';
import { ReferenceLineBadge } from 'geoviite-design-lib/alignment/reference-line-badge';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { useTranslation } from 'react-i18next';
import { ratkoPushFailed, ratkoPushInProgress } from 'ratko/ratko-model';
import { PublicationDetails } from 'publication/publication-model';
import { filterUnique } from 'utils/array-utils';

type PublicationListProps = {
    publications: PublicationDetails[];
    publicationClicked: (publication: PublicationDetails) => void;
    anyFailed: boolean;
};

const PUBLICATIONS_PER_PAGE = 8;

const getPublicationTrackNumbers = (publication: PublicationDetails) => {
    return [
        ...publication.trackNumbers.map((tn) => tn.id),
        ...publication.referenceLines.map((rl) => rl.trackNumberId),
        ...publication.locationTracks.map((lt) => lt.trackNumberId),
        ...publication.kmPosts.map((kp) => kp.trackNumberId),
    ].filter(filterUnique);
};

export const PublicationList: React.FC<PublicationListProps> = ({
    publications,
    publicationClicked,
    anyFailed,
}) => {
    const { t } = useTranslation();
    const [page, setPage] = React.useState(1);
    const [visiblePublications, setVisiblePublications] = React.useState<PublicationDetails[]>([]);
    //Track numbers rarely change, therefore we can always use the "latest" version
    const trackNumbers = useTrackNumbers('OFFICIAL') || [];

    React.useEffect(() => {
        setVisiblePublications(publications.slice(0, page * PUBLICATIONS_PER_PAGE));
    }, [page]);

    React.useEffect(() => {
        setPage(1);
    }, [publications]);

    const canExpand = visiblePublications.length < publications.length;

    const className = createClassName(
        styles['publication-list__more'],
        !canExpand && styles['publication-list__more--open'],
    );

    const showMoreOrHide = () => {
        if (visiblePublications.length < publications.length) {
            setPage(page + 1);
        } else {
            setPage(1);
        }
    };

    return (
        <React.Fragment>
            <div>
                {visiblePublications.map((publication, publicationIndex) => {
                    const isWaitingAfterFailure = anyFailed && publication.ratkoPushStatus === null;
                    const trackNumberIds = getPublicationTrackNumbers(publication);

                    return (
                        <div className={styles['publication-list-item']} key={publicationIndex}>
                            {publication.ratkoPushStatus === null && (
                                <div
                                    className={styles['publication-list-item__status--waiting']}
                                    title={t('publishing.waiting')}
                                />
                            )}

                            {ratkoPushInProgress(publication.ratkoPushStatus) && (
                                <div
                                    className={styles['publication-list-item__status--in-progress']}
                                    title={t('publishing.in-progress')}
                                />
                            )}
                            <div className={styles['publication-list-item__text']}>
                                <Link onClick={() => publicationClicked(publication)}>
                                    {formatDateFull(publication.publicationTime)}
                                </Link>
                            </div>
                            <div className={styles['publication-list-item__track-numbers']}>
                                {trackNumberIds.map((trackNumberId) => {
                                    const trackNumber = trackNumbers.find(
                                        (trackNumber) => trackNumber.id === trackNumberId,
                                    );

                                    return (
                                        trackNumber && (
                                            <span key={trackNumber.id}>
                                                <ReferenceLineBadge trackNumber={trackNumber} />
                                            </span>
                                        )
                                    );
                                })}
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
                                                size={IconSize.MEDIUM_SMALL}
                                            />
                                        </span>
                                    </div>
                                    <div className={styles['publication-list-item__text']}>
                                        {' '}
                                        {t('publishing.not-published')}
                                    </div>
                                </React.Fragment>
                            )}
                            {ratkoPushFailed(publication.ratkoPushStatus) && (
                                <React.Fragment>
                                    <div className={styles['publication-list-item__issue']}>
                                        <span
                                            className={
                                                styles['publication-list-item__issue--error']
                                            }>
                                            <Icons.StatusError
                                                color={IconColor.INHERIT}
                                                size={IconSize.MEDIUM_SMALL}
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
            <Link onClick={showMoreOrHide}>
                <span className={className}>
                    <Icons.Down size={IconSize.SMALL} color={IconColor.INHERIT} />
                </span>
                {canExpand ? t('publication-card.show-more') : t('publication-card.show-less')}
            </Link>
        </React.Fragment>
    );
};
