import { Table, Th } from 'vayla-design-lib/table/table';
import { PublicationTableRow } from 'publication/table/publication-table-row';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './publication-table.scss';
import { negComparator } from 'utils/array-utils';
import { PublicationDetails } from 'publication/publication-model';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import {
    getSortInfoForProp,
    InitiallyUnsorted,
    SortDirection,
    sortDirectionIcon,
    SortInformation,
    SortProps,
    toPublicationEntries,
} from './publication-table-utils';

export type PublicationTableProps = {
    publication: PublicationDetails;
};

const PublicationTable: React.FC<PublicationTableProps> = ({ publication }) => {
    const { t } = useTranslation();

    //Track numbers rarely change, therefore we can always use the "latest" version
    const trackNumbers = useTrackNumbers('OFFICIAL') || [];

    const [sortInfo, setSortInfo] = React.useState<SortInformation>(InitiallyUnsorted);
    const publicationEntries = toPublicationEntries(publication, trackNumbers);

    const sortedPublicationEntries =
        sortInfo && sortInfo.direction !== SortDirection.UNSORTED
            ? [...publicationEntries].sort(
                  sortInfo.direction == SortDirection.ASCENDING
                      ? sortInfo.function
                      : negComparator(sortInfo.function),
              )
            : publicationEntries;

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
                            SortProps.CHANGE_TIME,
                            'publication-table.publication-date',
                        )}
                        {sortableTableHeader(
                            SortProps.USER_NAME,
                            'publication-table.publication-user',
                        )}
                        {sortableTableHeader(SortProps.DEFINITION, 'publication-table.definition')}
                        {sortableTableHeader(
                            SortProps.RATKO_PUSH_DATE,
                            'publication-table.pushed-to-ratko',
                        )}
                    </tr>
                </thead>
                <tbody>
                    {sortedPublicationEntries.map((entry) => (
                        <PublicationTableRow
                            key={entry.name}
                            name={entry.name}
                            trackNumbers={entry.trackNumbers}
                            publicationTime={entry.publicationTime}
                            ratkoPushTime={entry.ratkoPushTime}
                            publicationUser={entry.publicationUser}
                            operation={entry.operation}
                            changedKmNumbers={entry.changedKmNumbers}
                            definition={entry.definition}
                        />
                    ))}
                </tbody>
            </Table>
        </div>
    );
};

export default PublicationTable;
