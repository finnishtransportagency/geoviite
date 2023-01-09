import { Table, Th } from 'vayla-design-lib/table/table';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './publication-log.scss';
import { Operation, PublicationDetails, PublicationId } from 'publication/publication-model';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { useLoader } from 'utils/react-utils';
import { getPublications } from 'publication/publication-api';
import {
    kmPostToLogTableEntry,
    locationTrackToLogTableEntry,
    referenceLineToLogTableEntry,
    switchesToLogTableEntry,
    trackNumberToLogTableEntry,
} from 'publication-log/publication-log-table-entry-mappings';

export type PublicationLogTableEntry = {
    id: PublicationId;
    name: string;
    trackNumber: string;
    operation: Operation;
    changeTime: string;
    userName: string;
    changedKmNumbers: string;
    definition: string;
};

type PublicationLogTableProps = {
    startDate: Date | undefined;
    endDate: Date | undefined;
};

const PublicationLogTable: React.FC<PublicationLogTableProps> = ({ startDate, endDate }) => {
    const { t } = useTranslation();

    const [trackNumbers, setTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);

    React.useEffect(() => {
        getTrackNumbers('OFFICIAL').then((trackNumbers) => setTrackNumbers(trackNumbers));
    }, []);

    const detailsToEntry = (publicationDetails: PublicationDetails): PublicationLogTableEntry[] => {
        const changeTime = publicationDetails.publicationTime;
        const userName = publicationDetails.publicationUser;

        const trackNums =
            (publicationDetails.trackNumbers &&
                publicationDetails.trackNumbers.map((t) => {
                    return trackNumberToLogTableEntry(t, changeTime, userName);
                })) ||
            [];
        const kmPosts: PublicationLogTableEntry[] =
            (publicationDetails.kmPosts &&
                publicationDetails.kmPosts.map((km) =>
                    kmPostToLogTableEntry(km, changeTime, userName, trackNumbers),
                )) ||
            [];
        const lTracks: PublicationLogTableEntry[] =
            (publicationDetails.locationTracks &&
                publicationDetails.locationTracks.map((l) =>
                    locationTrackToLogTableEntry(l, changeTime, userName, trackNumbers),
                )) ||
            [];
        const rLines =
            (publicationDetails.referenceLines &&
                publicationDetails.referenceLines.map((rl) =>
                    referenceLineToLogTableEntry(rl, changeTime, userName, trackNumbers),
                )) ||
            [];
        const switches =
            (publicationDetails.switches &&
                publicationDetails.switches.map((s) =>
                    switchesToLogTableEntry(s, changeTime, userName, trackNumbers),
                )) ||
            [];

        return trackNums.concat(kmPosts).concat(lTracks).concat(rLines).concat(switches);
    };

    const publicationDetailsList = useLoader(
        () => getPublications(startDate, endDate),
        [startDate, endDate],
    );

    const publicationLogTableEntries: PublicationLogTableEntry[] = publicationDetailsList
        ? publicationDetailsList.flatMap((details) => detailsToEntry(details))
        : [];

    return (
        <div className={styles['publication-table__container']}>
            <Table wide>
                <thead className={styles['publication-table__header']}>
                    <tr>
                        <Th>{t('publication-log-table.change-target')}</Th>
                        <Th>{t('publication-log-table.track-number-short')}</Th>
                        <Th>{t('publication-log-table.changed-km-numbers')}</Th>
                        <Th>{t('publication-log-table.operation')}</Th>
                        <Th>{t('publication-log-table.change-time')}</Th>
                        <Th>{t('publication-log-table.user')}</Th>
                        <Th>{t('publication-log-table.definition')}</Th>
                    </tr>
                </thead>
                <tbody>
                    {publicationLogTableEntries &&
                        publicationLogTableEntries.map((entry) => (
                            <React.Fragment key={entry.id}>
                                <tr className={'preview-table-item'}>
                                    <td>{entry.name}</td>
                                    <td>{entry.trackNumber}</td>
                                    <td>{entry.changedKmNumbers}</td>
                                    <td>{t(`operation.${entry.operation.toLowerCase()}`)}</td>
                                    <td>{entry.changeTime}</td>
                                    <td>{entry.userName}</td>
                                    <td>{entry.definition ? entry.definition : ''}</td>
                                </tr>
                            </React.Fragment>
                        ))}
                </tbody>
            </Table>
        </div>
    );
};

export default PublicationLogTable;
