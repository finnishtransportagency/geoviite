import * as React from 'react';
import PublicationTable from 'publication/table/publication-table';
import {
    PublicationDetails,
    PublicationId,
    PublicationTableItem,
} from 'publication/publication-model';
import styles from './publication.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { Link } from 'vayla-design-lib/link/link';
import { formatDateFull } from 'utils/date-utils';
import { ratkoPushFailed } from 'ratko/ratko-model';
import { getPublicationAsTableItems } from 'publication/publication-api';
import { TimeStamp } from 'common/common-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { useAppNavigate } from 'common/navigate';
import {
    InitiallyUnsorted,
    PublicationDetailsTableSortInformation,
} from 'publication/table/publication-table-utils';

export type PublicationDetailsViewProps = {
    publication: PublicationDetails;
    setSelectedPublicationId: (publicationId: PublicationId | undefined) => void;
    changeTime: TimeStamp;
};

const PublicationDetailsView: React.FC<PublicationDetailsViewProps> = ({
    publication,
    setSelectedPublicationId,
    changeTime,
}) => {
    const { t } = useTranslation();
    const navigate = useAppNavigate();

    const unpublishedToRatko = !publication.ratkoPushStatus;
    const [publicationItems, setPublicationItems] = React.useState<PublicationTableItem[]>([]);
    const [isLoading, setIsLoading] = React.useState(true);
    const [sortInfo, setSortInfo] =
        React.useState<PublicationDetailsTableSortInformation>(InitiallyUnsorted);

    React.useEffect(() => {
        setIsLoading(true);
        setSelectedPublicationId(publication.id);

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
                        setSelectedPublicationId(undefined);
                        navigate('frontpage');
                    }}>
                    {t('frontpage.frontpage-link')}
                </Link>
                <span className={styles['publication-details__publication-time']}>
                    {' > ' + formatDateFull(publication.publicationTime)}
                </span>
            </div>
            <div className={styles['publication-details__content']}>
                <div className={styles['publication-details__count-header']}>
                    {isLoading ? (
                        <React.Fragment>
                            {t('publication-table.count-header-loading')}&nbsp;
                            <Spinner />
                        </React.Fragment>
                    ) : (
                        <span>
                            {t('publication-table.count-header', {
                                number: publicationItems?.length || 0,
                                truncated: '',
                            })}
                        </span>
                    )}
                </div>
                <PublicationTable
                    isLoading={isLoading}
                    items={publicationItems}
                    sortInfo={sortInfo}
                    onSortChange={setSortInfo}
                />
            </div>
            {(ratkoPushFailed(publication.ratkoPushStatus) || unpublishedToRatko) && (
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
                    {unpublishedToRatko && (
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
                </footer>
            )}
        </div>
    );
};

export default PublicationDetailsView;
