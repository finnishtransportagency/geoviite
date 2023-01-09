import { Table, Th } from 'vayla-design-lib/table/table';
import { PreviewTableItemProps, PublicationTableItem } from 'publication/publication-table-item';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutTrackNumber, LayoutTrackNumberId } from 'track-layout/track-layout-model';
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
import { PublicationDetails } from 'publication/publication-model';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { RatkoPushStatus } from 'ratko/ratko-model';
import {
    getKmPostUiName,
    getLocationTrackUiName,
    getReferenceLineUiName,
    getSwitchUiName,
    getTrackNumberUiName,
} from 'preview/change-table-entry-mapping';

export type PublicationTableProps = {
    publication: PublicationDetails;
};

const toPublicationEntries = (
    publication: PublicationDetails,
    trackNumbers: LayoutTrackNumber[],
): PreviewTableItemProps[] => {
    const getTrackNumber = (id: LayoutTrackNumberId) => {
        return trackNumbers.find((tn) => tn.id == id)?.number;
    };

    const publicationInfo = {
        changeTime: publication.publicationTime,
        userName: publication.publicationUser,
        ratkoPushDate:
            publication.ratkoPushStatus === RatkoPushStatus.SUCCESSFUL
                ? publication.ratkoPushTime
                : null,
    };

    const trackNumberItems = publication.trackNumbers.map((trackNumber) => ({
        itemName: getTrackNumberUiName(trackNumber.number),
        trackNumber: trackNumber.number,
        operation: trackNumber.operation,
        ...publicationInfo,
    }));

    const referenceLines = publication.referenceLines.map((referenceLine) => ({
        itemName: getReferenceLineUiName(getTrackNumber(referenceLine.trackNumberId)),
        trackNumber: getTrackNumber(referenceLine.trackNumberId),
        operation: null,
        ...publicationInfo,
    }));

    const locationTracks = publication.locationTracks.map((locationTrack) => ({
        itemName: getLocationTrackUiName(locationTrack.name),
        trackNumber: getTrackNumber(locationTrack.trackNumberId),
        operation: locationTrack.operation,
        ...publicationInfo,
    }));

    const switches = publication.switches.map((s) => ({
        itemName: getSwitchUiName(s.name),
        trackNumber: s.trackNumberIds
            .map((id) => getTrackNumber(id))
            .sort()
            .join(', '),
        operation: s.operation,
        ...publicationInfo,
    }));

    const kmPosts = publication.kmPosts.map((kmPost) => ({
        itemName: getKmPostUiName(kmPost.kmNumber),
        trackNumber: getTrackNumber(kmPost.trackNumberId),
        operation: kmPost.operation,
        ...publicationInfo,
    }));

    return [...trackNumberItems, ...referenceLines, ...locationTracks, ...switches, ...kmPosts];
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
                        <PublicationTableItem
                            key={entry.itemName}
                            itemName={entry.itemName}
                            trackNumber={entry.trackNumber}
                            changeTime={entry.changeTime}
                            ratkoPushDate={entry.ratkoPushDate}
                            userName={entry.userName}
                            operation={entry.operation}
                        />
                    ))}
                </tbody>
            </Table>
        </div>
    );
};

export default PublicationTable;
