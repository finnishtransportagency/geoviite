import { Table, Th } from 'vayla-design-lib/table/table';
import PublicationTableRow from 'publication/table/publication-table-row';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './publication-table.scss';
import { PublicationId, PublicationTableItem } from 'publication/publication-model';
import {
    getSortInfoForProp,
    PublicationDetailsTableSortField,
    PublicationDetailsTableSortInformation,
} from './publication-table-utils';
import { getSortDirectionIcon, SortDirection } from 'utils/table-utils';
import { AccordionToggle } from 'vayla-design-lib/accordion-toggle/accordion-toggle';
import { useState } from 'react';
import { negComparator } from 'utils/array-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { useAppNavigate } from 'common/navigate';
import { SearchablePublicationLogItem } from 'publication/log/publication-log';
import { SearchItemValue } from 'tool-bar/search-dropdown';

export type PublicationTableProps = {
    items: PublicationTableItem[];
    sortInfo?: PublicationDetailsTableSortInformation;
    onSortChange?: (sortInfo: PublicationDetailsTableSortInformation) => void;
    isLoading?: boolean;
    displaySingleItemHistory: (
        item: SearchItemValue<SearchablePublicationLogItem> | undefined,
    ) => void;
};

const PublicationTable: React.FC<PublicationTableProps> = ({
    items,
    onSortChange,
    sortInfo,
    isLoading,
    displaySingleItemHistory,
}) => {
    const { t } = useTranslation();

    const sortByProp = (propName: PublicationDetailsTableSortField) => {
        if (sortInfo && onSortChange) {
            const newSortInfo = getSortInfoForProp(sortInfo.direction, sortInfo.propName, propName);
            onSortChange(newSortInfo);
        }
    };

    const sortableTableHeader = (
        prop: PublicationDetailsTableSortField,
        translationKey: string,
        className: string,
    ) => (
        <Th
            className={className}
            qa-id={translationKey}
            onClick={() => sortByProp(prop)}
            icon={
                sortInfo?.propName === prop ? getSortDirectionIcon(sortInfo.direction) : undefined
            }>
            {t(translationKey)}
        </Th>
    );

    const sortedPublicationTableItems =
        sortInfo && sortInfo.direction !== SortDirection.UNSORTED
            ? [...items].sort(
                  sortInfo.direction === SortDirection.ASCENDING
                      ? sortInfo.sortFunction
                      : negComparator(sortInfo.sortFunction),
              )
            : [...items];

    const [itemDetailsVisible, setItemDetailsVisible] = useState<PublicationTableItem['id'][]>([]);

    const publicationItemDetailsVisibilityToggle = React.useCallback(
        (id: PublicationTableItem['id']) =>
            setItemDetailsVisible((itemDetailsVisible) => {
                if (!itemDetailsVisible.includes(id)) {
                    return [...itemDetailsVisible, id];
                } else {
                    return itemDetailsVisible.filter((existingId) => existingId !== id);
                }
            }),
        [setItemDetailsVisible],
    );

    const anyPublicationItemDetailsVisible = itemDetailsVisible.length > 0;
    const toggleVisibilityOfAllDetails = () => {
        if (anyPublicationItemDetailsVisible) {
            setItemDetailsVisible([]);
        } else {
            setItemDetailsVisible(items.map((item) => item.id));
        }
    };
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const navigate = useAppNavigate();
    const displaySinglePublication = React.useCallback(
        (publicationId: PublicationId) => {
            navigate('publication-view', publicationId);
        },
        [delegates],
    );

    return (
        <div className={styles['publication-table']}>
            <Table wide isLoading={isLoading}>
                <thead className={styles['publication-table__table-header']}>
                    <tr>
                        <Th className={styles['publication-table__header--accordion']}>
                            <AccordionToggle
                                open={anyPublicationItemDetailsVisible}
                                onToggle={() => toggleVisibilityOfAllDetails()}
                            />
                        </Th>
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.NAME,
                            'publication-table.name',
                            styles['publication-table__header--name'],
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.TRACK_NUMBERS,
                            'publication-table.track-number',
                            styles['publication-table__header--track-number'],
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.CHANGED_KM_NUMBERS,
                            'publication-table.km-number',
                            styles['publication-table__header--km-number'],
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.OPERATION,
                            'publication-table.operation',
                            styles['publication-table__header--operation'],
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.PUBLICATION_TIME,
                            'publication-table.publication-time',
                            styles['publication-table__header--publication-time'],
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.PUBLICATION_USER,
                            'publication-table.publication-user',
                            styles['publication-table__header--user'],
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.MESSAGE,
                            'publication-table.message',
                            styles['publication-table__header--message'],
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.RATKO_PUSH_TIME,
                            'publication-table.pushed-to-ratko',
                            styles['publication-table__header--pushed-to-ratko'],
                        )}
                    </tr>
                </thead>
                <tbody>
                    {sortedPublicationTableItems.map((entry) => (
                        <PublicationTableRow
                            key={entry.id}
                            id={entry.id}
                            publicationId={entry.publicationId}
                            asset={entry.asset}
                            name={entry.name}
                            trackNumbers={entry.trackNumbers}
                            publicationTime={entry.publicationTime}
                            ratkoPushTime={entry.ratkoPushTime}
                            publicationUser={entry.publicationUser}
                            operation={entry.operation}
                            changedKmNumbers={entry.changedKmNumbers}
                            message={entry.message}
                            propChanges={entry.propChanges}
                            detailsVisible={itemDetailsVisible.includes(entry.id)}
                            detailsVisibleToggle={publicationItemDetailsVisibilityToggle}
                            displayItemHistory={displaySingleItemHistory}
                            displaySinglePublication={displaySinglePublication}
                        />
                    ))}
                </tbody>
            </Table>
        </div>
    );
};

export default PublicationTable;
