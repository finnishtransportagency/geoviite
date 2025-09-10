import React from 'react';
import { PublicationDetails } from 'publication/publication-model';
import { ratkoPushFailed, ratkoPushInProgress } from 'ratko/ratko-model';
import styles from 'publication/card/publication-list-row.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Spinner, SpinnerSize } from 'vayla-design-lib/spinner/spinner';
import { formatDateFull } from 'utils/date-utils';
import { RouterLink } from 'geoviite-design-lib/link/router-link';
import { PublicationListRowSplit } from 'publication/card/publication-list-row-split';

type MainPublicationListRowProps = {
    publication: PublicationDetails;
};

export const MainPublicationListRow: React.FC<MainPublicationListRowProps> = ({ publication }) => {
    return (
        <div className={styles['publication-list-item-container']}>
            <div className={styles['publication-list-item']}>
                <span className={styles['publication-list-item__timestamp']}>
                    {ratkoPushInProgress(publication.ratkoPushStatus) && (
                        <Spinner size={SpinnerSize.SMALL} />
                    )}
                    {ratkoPushFailed(publication.ratkoPushStatus) && (
                        <span className={styles['publication-list-item--error']}>
                            <Icons.StatusError size={IconSize.SMALL} color={IconColor.INHERIT} />
                        </span>
                    )}
                    <span className={styles['publication-list-item__header']}>
                        <RouterLink to={`/publications/${publication.id}`}>
                            {formatDateFull(publication.publicationTime)}
                        </RouterLink>
                    </span>
                </span>
                <span className={styles['publication-list-item__message']}>
                    {publication.message}
                </span>
            </div>
            {publication.split && (
                <PublicationListRowSplit
                    split={publication.split}
                    publicationId={publication.id}
                    ratkoPushStatus={publication.ratkoPushStatus}
                />
            )}
        </div>
    );
};
