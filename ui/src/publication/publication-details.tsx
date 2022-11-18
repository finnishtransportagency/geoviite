import * as React from 'react';
import PublicationTable from 'publication/publication-table';
import { getPublication } from 'publication/publication-api';
import {
    PublicationDetails,
    PublicationListingItem,
    ratkoPushFailed,
    RatkoPushStatus,
} from 'publication/publication-model';
import styles from './publication-details.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import RatkoPublishButton from 'publication/ratko-publish-button';
import { Link } from 'vayla-design-lib/link/link';
import { formatDateFull } from 'utils/date-utils';

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

    const [publishCandidates, setPublishCandidates] = React.useState<PublicationDetails | null >();
    const { t } = useTranslation();
    const waitingAfterFail = publication.status === null && anyFailed;

    React.useEffect(() => {
        let cancel = false;
        function fetchPublications () {
            getPublication(publication.id).then(result => {
                if (!cancel) {
                    setPublishCandidates(result)
                }
            });
        }

        fetchPublications();
        const intervalTimer = setInterval(fetchPublications, 30000)

        return () => {
            cancel = true;
            clearInterval(intervalTimer)
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
                    {publishCandidates && ' > ' + formatDateFull(publishCandidates.publishTime)}
                </span>
            </div>
            <div className={styles['publication-details__content']}>
                {publishCandidates && (
                    <PublicationTable
                        previewChanges={publishCandidates}
                        ratkoPushDate={
                            publishCandidates.status === RatkoPushStatus.SUCCESSFUL
                                ? publishCandidates.ratkoPushTime
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
                            <div
                                className={
                                    styles['publication-details__failure-notification--error']
                                }>
                                <Icons.StatusError
                                    color={IconColor.INHERIT}
                                    size={IconSize.MEDIUM}
                                />
                            </div>
                            <div
                                className={
                                    styles['publication-details__failure-notification__content']
                                }>
                                {t('publishing.publish-issue')}
                            </div>
                        </div>
                    )}
                    {waitingAfterFail && (
                        <div className={styles['publication-details__failure-notification']}>
                            <div
                                className={
                                    styles['publication-details__failure-notification--info']
                                }>
                                <Icons.Denied color={IconColor.INHERIT} size={IconSize.MEDIUM} />
                            </div>
                            <div
                                className={
                                    styles['publication-details__failure-notification__content']
                                }>
                                {t('publishing.not-published')}
                            </div>
                        </div>
                    )}
                    <RatkoPublishButton />
                </footer>
            )}
        </div>
    );
};

export default PublicationDetails;
