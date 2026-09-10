import * as React from 'react';
import PublicationTable from 'publication/table/publication-table';
import { PublicationDetails, PublicationId } from 'publication/publication-model';
import styles from './publication.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { formatDateFull } from 'utils/date-utils';
import { ratkoPushFailed } from 'ratko/ratko-model';
import { getPublicationAsTableItems } from 'publication/publication-api';
import { TimeStamp } from 'common/common-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { useNavigate } from 'react-router-dom';
import {
    SortablePublicationTableProps,
    SortedByNameAsc,
} from 'publication/table/publication-table-utils';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import { SearchItemType, SearchItemValue } from 'asset-search/search-dropdown';
import { publicationLogUrlForItem } from 'publication/log/publication-log-params';
import { TableSorting } from 'utils/table-utils';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';

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
    const navigate = useNavigate();

    const unpublishedToRatko = !publication.ratkoPushStatus;
    const [sortInfo, setSortInfo] =
        React.useState<TableSorting<SortablePublicationTableProps>>(SortedByNameAsc);

    const [publicationItemsOrUndefined, status] = useLoaderWithStatus(
        () => getPublicationAsTableItems(publication.id),
        [publication.id, changeTime],
    );
    const publicationItems = publicationItemsOrUndefined ?? [];

    const [loadedPublicationId, setLoadedPublicationId] = React.useState<PublicationId>();
    React.useEffect(() => {
        if (status === LoaderStatus.Ready) setLoadedPublicationId(publication.id);
    }, [publication.id, status]);

    React.useEffect(() => {
        setSelectedPublicationId(publication.id);
    }, [publication.id]);

    const displaySingleItemHistory = (item: SearchItemValue<SearchItemType> | undefined) => {
        navigate(item ? publicationLogUrlForItem(item) : '/publications');
    };

    return (
        <div className={styles['publication-details']}>
            <div className={styles['publication-details__title']}>
                <AnchorLink
                    onClick={() => {
                        setSelectedPublicationId(undefined);
                        navigate('/');
                    }}>
                    {t('frontpage.frontpage-link')}
                </AnchorLink>
                <span className={styles['publication-details__publication-time']}>
                    {' > ' + formatDateFull(publication.publicationTime)}
                </span>
            </div>
            <div className={styles['publication-details__content']}>
                <div className={styles['publication-details__count-header']}>
                    {status !== LoaderStatus.Ready ? (
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
                    // Table isLoading disables all interaction: do that only when switching
                    // publications, not on periodic reloads as only the status can really change
                    isLoading={publication.id !== loadedPublicationId}
                    items={publicationItems}
                    sortInfo={sortInfo}
                    onSortChange={setSortInfo}
                    displaySingleItemHistory={displaySingleItemHistory}
                    publicationDisplayMode={'SINGLE_PUBLICATION_DETAILS'}
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
