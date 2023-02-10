import { Table, Th } from 'vayla-design-lib/table/table';
import { PublicationTableRow } from 'publication/table/publication-table-row';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './publication-table.scss';
import { PublicationTableRowModel } from 'publication/publication-model';
import {
    getSortInfoForProp,
    sortDirectionIcon,
    SortInformation,
    SortProps,
} from './publication-table-utils';

export type PublicationTableProps = {
    truncated?: boolean;
    items: PublicationTableRowModel[];
    sortInfo?: SortInformation;
    onSortChange?: (sortInfo: SortInformation) => void;
};

const PublicationTable: React.FC<PublicationTableProps> = ({
    items,
    truncated,
    onSortChange,
    sortInfo,
}) => {
    const { t } = useTranslation();

    const sortByProp = (propName: SortProps) => {
        if (sortInfo && onSortChange) {
            const newSortInfo = getSortInfoForProp(sortInfo.direction, sortInfo.propName, propName);
            onSortChange(newSortInfo);
        }
    };

    const sortableTableHeader = (prop: SortProps, translationKey: string) => (
        <Th
            onClick={() => sortByProp(prop)}
            icon={sortInfo?.propName === prop ? sortDirectionIcon(sortInfo.direction) : undefined}>
            {t(translationKey)}
        </Th>
    );

    return (
        <div className={styles['publication-table__container']}>
            <div className={styles['publication-table__count-header']}>
                <span
                    title={
                        truncated ? t('publication-table.truncated', { number: items.length }) : ''
                    }>
                    {t('publication-table.count-header', {
                        number: items.length,
                        truncated: truncated ? '+' : '',
                    })}
                </span>
            </div>
            <Table wide>
                <thead className={styles['publication-table__header']}>
                    <tr>
                        {sortableTableHeader(SortProps.NAME, 'publication-table.name')}
                        {sortableTableHeader(
                            SortProps.TRACK_NUMBERS,
                            'publication-table.track-number',
                        )}
                        {sortableTableHeader(
                            SortProps.CHANGED_KM_NUMBERS,
                            'publication-table.km-number',
                        )}
                        {sortableTableHeader(SortProps.OPERATION, 'publication-table.operation')}
                        {sortableTableHeader(
                            SortProps.PUBLICATION_TIME,
                            'publication-table.publication-time',
                        )}
                        {sortableTableHeader(
                            SortProps.PUBLICATION_USER,
                            'publication-table.publication-user',
                        )}
                        {sortableTableHeader(SortProps.MESSAGE, 'publication-table.message')}
                        {sortableTableHeader(
                            SortProps.RATKO_PUSH_TIME,
                            'publication-table.pushed-to-ratko',
                        )}
                    </tr>
                </thead>
                <tbody>
                    {items.map((entry) => (
                        <PublicationTableRow
                            key={entry.id}
                            name={entry.name}
                            trackNumbers={entry.trackNumbers}
                            publicationTime={entry.publicationTime}
                            ratkoPushTime={entry.ratkoPushTime}
                            publicationUser={entry.publicationUser}
                            operation={entry.operation}
                            changedKmNumbers={entry.changedKmNumbers}
                            message={entry.message}
                        />
                    ))}
                </tbody>
            </Table>
        </div>
    );
};

export default PublicationTable;
