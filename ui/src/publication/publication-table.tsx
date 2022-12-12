import { Table, Th } from 'vayla-design-lib/table/table';
import { PublicationTableItem } from 'publication/publication-table-item';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import styles from './publication-table.scss';
import { negComparator } from 'utils/array-utils';
import {
    getSortInfoForProp,
    InitiallyUnsorted,
    SortDirection,
    sortDirectionIcon,
    SortInformation,
    SortProps,
} from 'preview/change-table-sorting';
import {
    trackNumberToChangeTableEntry,
    referenceLineToChangeTableEntry,
    locationTrackToChangeTableEntry,
    switchToChangeTableEntry,
    kmPostChangeTableEntry,
    ChangeTableEntry,
} from 'preview/change-table-entry-mapping';
import { PublishCandidates } from 'publication/publication-model';
import { TimeStamp } from 'common/common-model';

export type PublicationTableProps = {
    publicationChanges: PublishCandidates;
    ratkoPushDate: TimeStamp | undefined;
};

type PublicationTableEntry = ChangeTableEntry & { pushedToRatko: string | undefined };

const PublicationTable: React.FC<PublicationTableProps> = ({
    publicationChanges: changesInPublication,
    ratkoPushDate,
}) => {
    const { t } = useTranslation();
    const [trackNumbers, setTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);
    React.useEffect(() => {
        getTrackNumbers('OFFICIAL').then((trackNumbers) => setTrackNumbers(trackNumbers));
    }, []);

    const changesToPublicationEntries = (
        previewChanges: PublishCandidates,
    ): PublicationTableEntry[] =>
        previewChanges.trackNumbers
            .map((trackNumberCandidate) => ({
                ...trackNumberToChangeTableEntry(trackNumberCandidate, t),
                pushedToRatko: ratkoPushDate,
            }))
            .concat(
                previewChanges.referenceLines.map((referenceLineCandidate) => ({
                    ...referenceLineToChangeTableEntry(referenceLineCandidate, trackNumbers, t),
                    pushedToRatko: ratkoPushDate,
                })),
            )
            .concat(
                previewChanges.locationTracks.map((locationTrackCandidate) => ({
                    ...locationTrackToChangeTableEntry(locationTrackCandidate, trackNumbers, t),
                    pushedToRatko: ratkoPushDate,
                })),
            )
            .concat(
                previewChanges.switches.map((switchCandidate) => ({
                    ...switchToChangeTableEntry(switchCandidate, trackNumbers, t),
                    pushedToRatko: ratkoPushDate,
                })),
            )
            .concat(
                previewChanges.kmPosts.map((kmPostCandidate) => ({
                    ...kmPostChangeTableEntry(kmPostCandidate, trackNumbers, t),
                    pushedToRatko: ratkoPushDate,
                })),
            );

    const [sortInfo, setSortInfo] = React.useState<SortInformation>(InitiallyUnsorted);

    const publicationEntries = changesToPublicationEntries(changesInPublication);

    const sortedPublicationEntries =
        sortInfo && sortInfo.direction !== SortDirection.UNSORTED
            ? [...publicationEntries].sort(
                  sortInfo.direction == SortDirection.ASCENDING
                      ? sortInfo.function
                      : negComparator(sortInfo.function),
              )
            : [...publicationEntries];

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
                        {sortableTableHeader(SortProps.NAME, 'publication-table.change-target')}
                        {sortableTableHeader(
                            SortProps.TRACK_NUMBER,
                            'publication-table.track-number-short',
                        )}
                        {sortableTableHeader(SortProps.OPERATION, 'publication-table.change-type')}
                        {sortableTableHeader(
                            SortProps.CHANGE_TIME,
                            'publication-table.modified-moment',
                        )}
                        {sortableTableHeader(SortProps.USER_NAME, 'publication-table.user')}
                        {sortableTableHeader(
                            SortProps.PUSHED_TO_RATKO,
                            'publication-table.exported-to-ratko',
                        )}
                    </tr>
                </thead>
                <tbody>
                    {sortedPublicationEntries.map((entry) => (
                        <React.Fragment key={entry.id}>
                            {
                                <PublicationTableItem
                                    itemName={entry.uiName}
                                    trackNumber={entry.trackNumber}
                                    changeTime={entry.changeTime}
                                    ratkoPushDate={ratkoPushDate}
                                    userName={entry.userName}
                                    operation={entry.operation}
                                />
                            }
                        </React.Fragment>
                    ))}
                </tbody>
            </Table>
        </div>
    );
};

export default PublicationTable;
