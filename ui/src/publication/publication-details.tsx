import * as React from 'react';
import PublicationTable from 'publication/publication-table';
import { getPublication } from 'publication/publication-api';
import { PublicationDetails, PublicationListingItem } from 'publication/publication-model';
import styles from './publication-details.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import RatkoPublishButton from 'ratko/ratko-publish-button';
import { Link } from 'vayla-design-lib/link/link';
import { formatDateFull } from 'utils/date-utils';
import { ratkoPushFailed, RatkoPushStatus } from 'ratko/ratko-model';

export type PublicationDetailsProps = {
    publication: PublicationListingItem;
    onPublicationUnselected: () => void;
    anyFailed: boolean;
};

const PublicationDetails: React.FC<PublicationDetailsProps> = ({
    publication,
    onPublicationUnselected,
    anyFailed,
}) => {
    const [publicationDetails, setPublicationDetails] = React.useState<PublicationDetails | null>();
    const [waitingAfterFail, setWaitingAfterFail] = React.useState<boolean>();
    const { t } = useTranslation();

    React.useEffect(() => {
        let cancel = false;

        function fetchPublications() {
            getPublication(publication.id).then((result) => {
                if (!cancel) {
                    setPublicationDetails(result);
                }
            });
        }

        fetchPublications();
        const intervalTimer = setInterval(fetchPublications, 30000);
        setWaitingAfterFail(publication.status === null && anyFailed);

        return () => {
            cancel = true;
            clearInterval(intervalTimer);
        };
    }, []);

    return (
        <div className={styles['publication-details__publication']}>
            <div className={styles['publication-details__title']}>
                <Link
                    onClick={() => {
                        onPublicationUnselected();
                    }}>
                    {t('frontpage.frontpage-link')}
                </Link>
                <span style={{ whiteSpace: 'pre' }}>
                    {publicationDetails && ' > ' + formatDateFull(publicationDetails.publishTime)}
                </span>
            </div>
            <div className={styles['publication-details__content']}>
                {publicationDetails && (
                    <PublicationTable
                        previewChanges={publicationDetails}
                        ratkoPushDate={
                            publicationDetails.status === RatkoPushStatus.SUCCESSFUL
                                ? publicationDetails.ratkoPushTime || undefined
                                : undefined
                        }
                        showRatkoPushDate={true}
                    />
                )}
            </div>
            {(ratkoPushFailed(publication.status) || waitingAfterFail) && (
                <footer className={styles['publication-details__footer']}>
                    {ratkoPushFailed(publication.status) && (
                        <div className={styles['publication-details__failure-notification']}>
                            <span
                                className={
                                    styles['publication-details__failure-notification--error']
                                }>
                                <Icons.StatusError
                                    color={IconColor.INHERIT}
                                    size={IconSize.MEDIUM}
                                />
                            </span>
                            <span
                                className={
                                    styles['publication-details__failure-notification__content']
                                }>
                                {t('publishing.publish-issue')}
                            </span>
                        </div>
                    )}
                    {waitingAfterFail && (
                        <div className={styles['publication-details__failure-notification']}>
                            <span
                                className={
                                    styles['publication-details__failure-notification--info']
                                }>
                                <Icons.Denied color={IconColor.INHERIT} size={IconSize.MEDIUM} />
                            </span>
                            <span
                                className={
                                    styles['publication-details__failure-notification__content']
                                }>
                                {t('publishing.not-published')}
                            </span>
                        </div>
                    )}
                    <RatkoPublishButton />
                </footer>
            )}
        </div>
    );
};

export default PublicationDetails;
