import * as React from 'react';
import PublicationTable from 'publication/publication-table';
import { getPublication } from 'publication/publication-api';
import { PublicationDetails as PublicationDetailsModel } from 'publication/publication-model';
import styles from './publication-details.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import RatkoPublishButton from 'ratko/ratko-publish-button';
import { Link } from 'vayla-design-lib/link/link';
import { formatDateFull } from 'utils/date-utils';
import { useLoaderWithTimer } from 'utils/react-utils';
import { ratkoPushFailed } from 'ratko/ratko-model';

export type PublicationDetailsProps = {
    publication: PublicationDetailsModel;
    onPublicationUnselected: () => void;
    anyFailed: boolean;
};

const PublicationDetails: React.FC<PublicationDetailsProps> = ({
    publication,
    onPublicationUnselected,
    anyFailed,
}) => {
    const [publicationDetails, setPublicationDetails] =
        React.useState<PublicationDetailsModel | null>();
    const [waitingAfterFail, setWaitingAfterFail] = React.useState<boolean>();
    const { t } = useTranslation();

    function setPublicationDetailsAndWaitingAfterFail(details?: PublicationDetailsModel) {
        setPublicationDetails(details);
        setWaitingAfterFail(publicationDetails?.ratkoPushStatus === null && anyFailed);
    }

    useLoaderWithTimer(
        setPublicationDetailsAndWaitingAfterFail,
        () => getPublication(publication.id),
        [],
        30000,
    );

    return (
        <div className={styles['publication-details__publication']}>
            <div className={styles['publication-details__title']}>
                <Link
                    onClick={() => {
                        onPublicationUnselected();
                    }}>
                    {t('frontpage.frontpage-link')}
                </Link>
                <span className={styles['publication-details__publication-time']}>
                    {publicationDetails &&
                        ' > ' + formatDateFull(publicationDetails.publicationTime)}
                </span>
            </div>
            <div className={styles['publication-details__content']}>
                {publicationDetails && <PublicationTable publication={publicationDetails} />}
            </div>
            {(ratkoPushFailed(publication.ratkoPushStatus) || waitingAfterFail) && (
                <footer className={styles['publication-details__footer']}>
                    {ratkoPushFailed(publication.ratkoPushStatus) && (
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
