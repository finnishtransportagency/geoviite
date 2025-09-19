import { Table, Th } from 'vayla-design-lib/table/table';
import PublicationTableRow from 'publication/table/publication-table-row';
import * as React from 'react';
import styles from './publication-table.scss';
import { PublicationId, PublicationTableItem } from 'publication/publication-model';
import { getSortInfoForProp, SortablePublicationTableProps } from './publication-table-utils';
import { SortDirection, TableSorting } from 'utils/table-utils';
import { AccordionToggle } from 'vayla-design-lib/accordion-toggle/accordion-toggle';
import { useState } from 'react';
import { negComparator } from 'utils/array-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { useAppNavigate } from 'common/navigate';
import { SearchablePublicationLogItem } from 'publication/log/publication-log';
import { SearchItemValue } from 'asset-search/search-dropdown';
import { SortableTableHeader } from 'vayla-design-lib/table/sortable-table-header';
import { useTrackNumbersIncludingDeleted } from 'track-layout/track-layout-react-utils';
import { LayoutContext } from 'common/common-model';

export type PublicationTableProps = {
    layoutContext: LayoutContext;
    items: PublicationTableItem[];
    sortInfo: TableSorting<SortablePublicationTableProps>;
    onSortChange: (sortInfo: TableSorting<SortablePublicationTableProps>) => void;
    isLoading?: boolean;
    displaySingleItemHistory: (
        item: SearchItemValue<SearchablePublicationLogItem> | undefined,
    ) => void;
};

const PublicationTable: React.FC<PublicationTableProps> = ({
    layoutContext,
    items,
    onSortChange,
    sortInfo,
    isLoading,
    displaySingleItemHistory,
}) => {
    const sortByProp = (propName: keyof SortablePublicationTableProps) => {
        if (sortInfo && onSortChange) {
            const newSortInfo = getSortInfoForProp(sortInfo.direction, sortInfo.propName, propName);
            onSortChange(newSortInfo);
        }
    };

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
    const trackNumbers = useTrackNumbersIncludingDeleted(layoutContext) ?? [];

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
                        <SortableTableHeader
                            prop={'name'}
                            translationKey={'publication-table.name'}
                            className={styles['publication-table__header--name']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'trackNumbers'}
                            translationKey={'publication-table.track-number'}
                            className={styles['publication-table__header--track-number']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'changedKmNumbers'}
                            translationKey={'publication-table.km-number'}
                            className={styles['publication-table__header--km-number']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'operation'}
                            translationKey={'publication-table.operation'}
                            className={styles['publication-table__header--operation']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'publicationTime'}
                            translationKey={'publication-table.publication-time'}
                            className={styles['publication-table__header--publication-time']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'publicationUser'}
                            translationKey={'publication-table.publication-user'}
                            className={styles['publication-table__header--user']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'message'}
                            translationKey={'publication-table.message'}
                            className={styles['publication-table__header--message']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'ratkoPushTime'}
                            translationKey={'publication-table.pushed-to-ratko'}
                            className={styles['publication-table__header--pushed-to-ratko']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
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
                            allLayoutTrackNumbers={trackNumbers}
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
