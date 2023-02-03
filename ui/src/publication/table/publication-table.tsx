import { Table, Th } from 'vayla-design-lib/table/table';
import {
    PublicationTableRow,
    PublicationTableRowProps,
} from 'publication/table/publication-table-row';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './publication-table.scss';
import { negComparator } from 'utils/array-utils';
import { PublicationDetails } from 'publication/publication-model';
import { useTrackNumbersIncludingDeleted } from 'track-layout/track-layout-react-utils';
import {
    getSortInfoForProp,
    InitiallyUnsorted,
    SortDirection,
    sortDirectionIcon,
    SortInformation,
    SortProps,
    toPublicationTableRows,
} from './publication-table-utils';

export type PublicationTableProps = {
    publications: PublicationDetails[];
};

const PublicationTable: React.FC<PublicationTableProps> = ({ publications }) => {
    const { t } = useTranslation();

    //Track numbers rarely change, therefore we can always use the "latest" version
    const trackNumbers = useTrackNumbersIncludingDeleted('OFFICIAL');

    const [sortInfo, setSortInfo] = React.useState<SortInformation>(InitiallyUnsorted);
    const [sortedPublicationRows, setSortedPublicationRows] = React.useState<
        PublicationTableRowProps[]
    >([]);

    React.useEffect(() => {
        if (trackNumbers) {
            const publicationRows = publications.flatMap((p) =>
                toPublicationTableRows(p, trackNumbers),
            );

            if (sortInfo.direction !== SortDirection.UNSORTED) {
                publicationRows.sort(
                    sortInfo.direction == SortDirection.ASCENDING
                        ? sortInfo.function
                        : negComparator(sortInfo.function),
                );
            }

            setSortedPublicationRows(publicationRows);
        }
    }, [publications, sortInfo, trackNumbers]);

    const sortByProp = (propName: SortProps) => {
        const newSortInfo = getSortInfoForProp(sortInfo.direction, sortInfo.propName, propName);
        setSortInfo(newSortInfo);
    };

    const sortableTableHeader = (prop: SortProps, translationKey: string) => (
        <Th
            onClick={() => sortByProp(prop)}
            icon={sortInfo.propName === prop ? sortDirectionIcon(sortInfo.direction) : undefined}>
            {t(translationKey)}
        </Th>
    );

    return (
        <div className={styles['publication-table__container']}>
            <div className={styles['publication-table__count-header']}>
                {sortedPublicationRows &&
                    t('publication-table.count-header', { number: sortedPublicationRows.length })}
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
                        {sortableTableHeader(SortProps.DEFINITION, 'publication-table.definition')}
                        {sortableTableHeader(
                            SortProps.RATKO_PUSH_TIME,
                            'publication-table.pushed-to-ratko',
                        )}
                    </tr>
                </thead>
                <tbody>
                    {sortedPublicationRows.map((entry) => (
                        <PublicationTableRow
                            key={`${entry.name}_${entry.publicationTime}`}
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
