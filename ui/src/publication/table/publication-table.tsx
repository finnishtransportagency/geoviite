import { Table, Th } from 'vayla-design-lib/table/table';
import PublicationTableRow from 'publication/table/publication-table-row';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './publication-table.scss';
import { PublicationId, PublicationTableItem } from 'publication/publication-model';
import { getSortInfoForProp, SortablePublicationTableProps } from './publication-table-utils';
import { getSortDirectionIcon, SortDirection, TableSorting } from 'utils/table-utils';
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
    sortInfo?: TableSorting<SortablePublicationTableProps>;
    onSortChange?: (sortInfo: TableSorting<SortablePublicationTableProps>) => void;
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

    const sortByProp = (propName: keyof SortablePublicationTableProps) => {
        if (sortInfo && onSortChange) {
            const newSortInfo = getSortInfoForProp(sortInfo.direction, sortInfo.propName, propName);
            onSortChange(newSortInfo);
        }
    };

    const sortableTableHeader = (
        prop: keyof SortablePublicationTableProps,
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
                      ? sortInfo.function
                      : negComparator(sortInfo.function),
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
                            'name',
                            'publication-table.name',
                            styles['publication-table__header--name'],
                        )}
                        {sortableTableHeader(
                            'trackNumbers',
                            'publication-table.track-number',
                            styles['publication-table__header--track-number'],
                        )}
                        {sortableTableHeader(
                            'changedKmNumbers',
                            'publication-table.km-number',
                            styles['publication-table__header--km-number'],
                        )}
                        {sortableTableHeader(
                            'operation',
                            'publication-table.operation',
                            styles['publication-table__header--operation'],
                        )}
                        {sortableTableHeader(
                            'publicationTime',
                            'publication-table.publication-time',
                            styles['publication-table__header--publication-time'],
                        )}
                        {sortableTableHeader(
                            'publicationUser',
                            'publication-table.publication-user',
                            styles['publication-table__header--user'],
                        )}
                        {sortableTableHeader(
                            'message',
                            'publication-table.message',
                            styles['publication-table__header--message'],
                        )}
                        {sortableTableHeader(
                            'ratkoPushTime',
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
