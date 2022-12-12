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
    ChangeTableProps,
    trackNumberToChangeTableEntry,
    referenceLineToChangeTableEntry,
    locationTrackToChangeTableEntry,
    switchToChangeTableEntry,
    kmPostChangeTableEntry,
} from 'preview/change-table-entry-mapping';
import { PublishCandidates } from 'publication/publication-model';

const PublicationTable: React.FC<ChangeTableProps> = ({
    previewChanges,
    ratkoPushDate = undefined,
}) => {
    const { t } = useTranslation();
    const [trackNumbers, setTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);
    React.useEffect(() => {
        getTrackNumbers('OFFICIAL').then((trackNumbers) => setTrackNumbers(trackNumbers));
    }, []);

    const changesToPublicationEntries = (previewChanges: PublishCandidates) =>
        previewChanges.trackNumbers
            .map((trackNumberCandidate) => trackNumberToChangeTableEntry(trackNumberCandidate, t))
            .concat(
                previewChanges.referenceLines.map((referenceLineCandidate) =>
                    referenceLineToChangeTableEntry(referenceLineCandidate, trackNumbers, t),
                ),
            )
            .concat(
                previewChanges.locationTracks.map((locationTrackCandidate) =>
                    locationTrackToChangeTableEntry(locationTrackCandidate, trackNumbers, t),
                ),
            )
            .concat(
                previewChanges.switches.map((switchCandidate) =>
                    switchToChangeTableEntry(switchCandidate, trackNumbers, t),
                ),
            )
            .concat(
                previewChanges.kmPosts.map((kmPostCandidate) =>
                    kmPostChangeTableEntry(kmPostCandidate, trackNumbers, t),
                ),
            );

    const [sortInfo, setSortInfo] = React.useState<SortInformation>(InitiallyUnsorted);

    const publicationEntries = changesToPublicationEntries(previewChanges);

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

    return (
        <div className={styles['publication-table__container']}>
            <Table wide>
                <thead className={styles['publication-table__header']}>
                    <tr>
                        <Th
                            onClick={() => sortByProp(SortProps.NAME)}
                            icon={
                                sortInfo.propName === SortProps.NAME
                                    ? sortDirectionIcon(sortInfo.direction)
                                    : undefined
                            }>
                            {t('publication-table.change-target')}
                        </Th>
                        <Th
                            onClick={() => sortByProp(SortProps.TRACK_NUMBER)}
                            icon={
                                sortInfo.propName === SortProps.TRACK_NUMBER
                                    ? sortDirectionIcon(sortInfo.direction)
                                    : undefined
                            }>
                            {t('publication-table.track-number-short')}
                        </Th>
                        <Th
                            onClick={() => sortByProp(SortProps.OPERATION)}
                            icon={
                                sortInfo.propName === SortProps.OPERATION
                                    ? sortDirectionIcon(sortInfo.direction)
                                    : undefined
                            }>
                            {t('publication-table.change-type')}
                        </Th>
                        <Th
                            onClick={() => sortByProp(SortProps.CHANGE_TIME)}
                            icon={
                                sortInfo.propName === SortProps.CHANGE_TIME
                                    ? sortDirectionIcon(sortInfo.direction)
                                    : undefined
                            }>
                            {t('publication-table.modified-moment')}
                        </Th>
                        <Th
                            onClick={() => sortByProp(SortProps.USER_NAME)}
                            icon={
                                sortInfo.propName === SortProps.USER_NAME
                                    ? sortDirectionIcon(sortInfo.direction)
                                    : undefined
                            }>
                            {t('publication-table.user')}
                        </Th>
                        <Th>{t('publication-table.exported-to-ratko')}</Th>
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
