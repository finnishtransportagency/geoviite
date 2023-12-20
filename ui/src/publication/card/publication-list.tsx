import React from 'react';
import styles from './publication-list.scss';
import { Link } from 'vayla-design-lib/link/link';
import { formatDateFull } from 'utils/date-utils';
import { ReferenceLineBadge } from 'geoviite-design-lib/alignment/reference-line-badge';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { useTranslation } from 'react-i18next';
import { ratkoPushFailed, ratkoPushInProgress } from 'ratko/ratko-model';
import { PublicationDetails } from 'publication/publication-model';
import { filterUnique } from 'utils/array-utils';
import { useAppNavigate } from 'common/navigate';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';

type PublicationListProps = {
    publications: PublicationDetails[];
    anyFailed?: boolean;
};

const getPublicationTrackNumbers = (publication: PublicationDetails) => {
    return [
        ...publication.trackNumbers.map((tn) => tn.id),
        ...publication.referenceLines.map((rl) => rl.trackNumberId),
        ...publication.locationTracks.map((lt) => lt.trackNumberId),
        ...publication.kmPosts.map((kp) => kp.trackNumberId),
    ].filter(filterUnique);
};

export const PublicationList: React.FC<PublicationListProps> = ({ publications, anyFailed }) => {
    const { t } = useTranslation();
    const navigate = useAppNavigate();

    const trackLayoutActionDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    //Track numbers rarely change, therefore we can always use the "latest" version
    const trackNumbers = useTrackNumbers('OFFICIAL') || [];

    return (
        <div qa-id="publication-list">
            {publications.map((publication) => {
                const isWaitingAfterFailure = anyFailed && !publication.ratkoPushStatus;
                const trackNumberIds = getPublicationTrackNumbers(publication);

                return (
                    <div className={styles['publication-list-item']} key={publication.id}>
                        {!publication.ratkoPushStatus && (
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
                            <Link
                                onClick={() => {
                                    trackLayoutActionDelegates.setSelectedPublicationId(
                                        publication.id,
                                    );
                                    navigate('publication-view', publication.id);
                                }}>
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
                                        <ReferenceLineBadge
                                            key={trackNumber.id}
                                            trackNumber={trackNumber}
                                        />
                                    )
                                );
                            })}
                        </div>
                        {isWaitingAfterFailure && (
                            <React.Fragment>
                                <div className={styles['publication-list-item__issue']}>
                                    <span className={styles['publication-list-item__issue--info']}>
                                        <Icons.Denied
                                            color={IconColor.INHERIT}
                                            size={IconSize.MEDIUM_SMALL}
                                        />
                                    </span>
                                </div>
                                <div className={styles['publication-list-item__text']}>
                                    {' ' + t('publishing.not-published')}
                                </div>
                            </React.Fragment>
                        )}
                        {ratkoPushFailed(publication.ratkoPushStatus) && (
                            <React.Fragment>
                                <div className={styles['publication-list-item__issue']}>
                                    <span className={styles['publication-list-item__issue--error']}>
                                        <Icons.StatusError
                                            color={IconColor.INHERIT}
                                            size={IconSize.MEDIUM_SMALL}
                                        />
                                    </span>
                                </div>
                                <div className={styles['publication-list-item__text']}>
                                    {' ' + t('publishing.publish-issue')}
                                </div>
                            </React.Fragment>
                        )}
                    </div>
                );
            })}
        </div>
    );
};
