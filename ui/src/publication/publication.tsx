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
import { formatDateFull } from 'utils/date-utils';
import { ratkoPushFailed } from 'ratko/ratko-model';
import { getPublicationAsTableItems } from 'publication/publication-api';
import { LayoutContext, TimeStamp } from 'common/common-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { useAppNavigate } from 'common/navigate';
import {
    SortablePublicationTableProps,
    SortedByNameAsc,
} from 'publication/table/publication-table-utils';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { SearchItemValue } from 'tool-bar/search-dropdown';
import { SearchablePublicationLogItem } from 'publication/log/publication-log';
import { START_OF_2022 } from 'vayla-design-lib/datepicker/datepicker';
import { TableSorting } from 'utils/table-utils';

export type PublicationDetailsViewProps = {
    layoutContext: LayoutContext;
    publication: PublicationDetails;
    setSelectedPublicationId: (publicationId: PublicationId | undefined) => void;
    changeTime: TimeStamp;
};

const PublicationDetailsView: React.FC<PublicationDetailsViewProps> = ({
    layoutContext,
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
        React.useState<TableSorting<SortablePublicationTableProps>>(SortedByNameAsc);
    const trackLayoutActionDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    React.useEffect(() => {
        setIsLoading(true);
        setSelectedPublicationId(publication.id);

        getPublicationAsTableItems(publication.id).then((p) => {
            p && setPublicationItems(p);
            setIsLoading(false);
        });
    }, [publication.id, changeTime]);

    const displaySingleItemHistory = (
        item: SearchItemValue<SearchablePublicationLogItem> | undefined,
    ) => {
        trackLayoutActionDelegates.setSelectedPublicationSearchStartDate(
            START_OF_2022.toISOString(),
        );
        trackLayoutActionDelegates.setSelectedPublicationSearchEndDate(new Date().toISOString());
        trackLayoutActionDelegates.setSelectedPublicationSearchSearchableItem(item);
        navigate('publication-search');
    };

    return (
        <div className={styles['publication-details']}>
            <div className={styles['publication-details__title']}>
                <AnchorLink
                    onClick={() => {
                        setSelectedPublicationId(undefined);
                        navigate('frontpage');
                    }}>
                    {t('frontpage.frontpage-link')}
                </AnchorLink>
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
                    layoutContext={layoutContext}
                    isLoading={isLoading}
                    items={publicationItems}
                    sortInfo={sortInfo}
                    onSortChange={setSortInfo}
                    displaySingleItemHistory={displaySingleItemHistory}
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
