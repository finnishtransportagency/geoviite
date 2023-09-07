import * as React from 'react';
import PublicationTable from 'publication/table/publication-table';
import { PublicationDetails, PublicationTableItem } from 'publication/publication-model';
import styles from './publication.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import RatkoPublishButton from 'ratko/ratko-publish-button';
import { Link } from 'vayla-design-lib/link/link';
import { formatDateFull } from 'utils/date-utils';
import { ratkoPushFailed } from 'ratko/ratko-model';
import { getPublicationAsTableItems } from 'publication/publication-api';
import { TimeStamp } from 'common/common-model';

export type PublicationDetailsViewProps = {
    publication: PublicationDetails;
    onPublicationUnselected: () => void;
    anyFailed: boolean;
    changeTime: TimeStamp;
};

const PublicationDetailsView: React.FC<PublicationDetailsViewProps> = ({
    publication,
    onPublicationUnselected,
    anyFailed,
    changeTime,
}) => {
    const { t } = useTranslation();

    const waitingAfterFail = !publication.ratkoPushStatus && anyFailed;
    const [publicationItems, setPublicationItems] = React.useState<PublicationTableItem[]>([]);
    const [isLoading, setIsLoading] = React.useState(true);

    React.useEffect(() => {
        setIsLoading(true);

        getPublicationAsTableItems(publication.id).then((p) => {
            p && setPublicationItems(p);
            setIsLoading(false);
        });
    }, [publication.id, changeTime]);

    return (
        <div className={styles['publication-details']}>
            <div className={styles['publication-details__title']}>
                <Link
                    onClick={() => {
                        onPublicationUnselected();
                    }}>
                    {t('frontpage.frontpage-link')}
                </Link>
                <span className={styles['publication-details__publication-time']}>
                    {' > ' + formatDateFull(publication.publicationTime)}
                </span>
            </div>
            <div className={styles['publication-details__content']}>
                <PublicationTable isLoading={isLoading} items={publicationItems} />
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

export default PublicationDetailsView;
