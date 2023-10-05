import { Table, Th } from 'vayla-design-lib/table/table';
import { PublicationTableRow } from 'publication/table/publication-table-row';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './publication-table.scss';
import { PublicationTableItem } from 'publication/publication-model';
import {
    getSortInfoForProp,
    PublicationDetailsTableSortField,
    PublicationDetailsTableSortInformation,
} from './publication-table-utils';
import { getSortDirectionIcon } from 'utils/table-utils';

export type PublicationTableProps = {
    items: PublicationTableItem[];
    sortInfo?: PublicationDetailsTableSortInformation;
    onSortChange?: (sortInfo: PublicationDetailsTableSortInformation) => void;
    isLoading?: boolean;
};

const PublicationTable: React.FC<PublicationTableProps> = ({
    items,
    onSortChange,
    sortInfo,
    isLoading,
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
    ) => (
        <Th
            onClick={() => sortByProp(prop)}
            icon={
                sortInfo?.propName === prop ? getSortDirectionIcon(sortInfo.direction) : undefined
            }>
            {t(translationKey)}
        </Th>
    );

    return (
        <div className={styles['publication-table']}>
            <Table wide isLoading={isLoading}>
                <thead className={styles['publication-table__table-header']}>
                    <tr>
                        <Th />
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.NAME,
                            'publication-table.name',
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.TRACK_NUMBERS,
                            'publication-table.track-number',
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.CHANGED_KM_NUMBERS,
                            'publication-table.km-number',
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.OPERATION,
                            'publication-table.operation',
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.PUBLICATION_TIME,
                            'publication-table.publication-time',
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.PUBLICATION_USER,
                            'publication-table.publication-user',
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.MESSAGE,
                            'publication-table.message',
                        )}
                        {sortableTableHeader(
                            PublicationDetailsTableSortField.RATKO_PUSH_TIME,
                            'publication-table.pushed-to-ratko',
                        )}
                    </tr>
                </thead>
                <tbody>
                    {items.map((entry) => (
                        <PublicationTableRow
                            key={entry.id}
                            id={entry.id}
                            name={entry.name}
                            trackNumbers={entry.trackNumbers}
                            publicationTime={entry.publicationTime}
                            ratkoPushTime={entry.ratkoPushTime}
                            publicationUser={entry.publicationUser}
                            operation={entry.operation}
                            changedKmNumbers={entry.changedKmNumbers}
                            message={entry.message}
                            propChanges={entry.propChanges}
                        />
                    ))}
                </tbody>
            </Table>
        </div>
    );
};

export default PublicationTable;
