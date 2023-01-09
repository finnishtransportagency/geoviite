import { Table, Th } from 'vayla-design-lib/table/table';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './publication-log.scss';
import { PublicationDetails } from 'publication/publication-model';
import { useLoader } from 'utils/react-utils';
import { getPublications } from 'publication/publication-api';
import {
    getPublicationLogTableEntryCommonFields,
    kmPostToLogTableEntry,
    locationTrackToLogTableEntry,
    PublicationLogTableEntry,
    referenceLineToLogTableEntry,
    switchesToLogTableEntry,
    trackNumberToLogTableEntry,
} from 'publication/log/publication-log-table-entry-mappings';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { addDays, startOfDay } from 'date-fns';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { formatDateFull } from 'utils/date-utils';

type PublicationLogTableProps = {
    startDate: Date | undefined;
    endDate: Date | undefined;
};

const detailsToEntry = (
    publicationDetails: PublicationDetails,
    trackNumbers: LayoutTrackNumber[],
): PublicationLogTableEntry[] => {
    const commonFields = getPublicationLogTableEntryCommonFields(publicationDetails);

    const trackNums = publicationDetails.trackNumbers.map((t) =>
        trackNumberToLogTableEntry(t, trackNumbers, commonFields),
    );

    const kmPosts = publicationDetails.kmPosts.map((km) =>
        kmPostToLogTableEntry(km, trackNumbers, commonFields),
    );

    const lTracks = publicationDetails.locationTracks.map((l) =>
        locationTrackToLogTableEntry(l, trackNumbers, commonFields),
    );

    const rLines = publicationDetails.referenceLines.map((rl) =>
        referenceLineToLogTableEntry(rl, trackNumbers, commonFields),
    );

    const switches = publicationDetails.switches.map((s) =>
        switchesToLogTableEntry(s, trackNumbers, commonFields),
    );

    return [...trackNums, ...kmPosts, ...lTracks, ...rLines, ...switches];
};

const PublicationLogTable: React.FC<PublicationLogTableProps> = ({ startDate, endDate }) => {
    const { t } = useTranslation();

    const trackNumbers = useTrackNumbers('OFFICIAL') || [];

    const publicationDetailsList = useLoader(() => {
        const toDate = endDate ? startOfDay(addDays(endDate, 1)) : undefined;
        return getPublications(startDate, toDate);
    }, [startDate, endDate]);

    const publicationLogTableEntries = publicationDetailsList?.flatMap((details) =>
        detailsToEntry(details, trackNumbers),
    );

    return (
        <div className={styles['publication-log-table__container']}>
            <Table wide>
                <thead className={styles['publication-log-table__header']}>
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
                    {publicationLogTableEntries?.map((entry) => (
                        <tr key={`${entry.publicationId}_${entry.name}`}>
                            <td>{entry.name}</td>
                            <td>{entry.trackNumbers.sort().join(', ')}</td>
                            <td>{entry.changedKmNumbers}</td>
                            <td>
                                {entry.operation
                                    ? t(`enum.publish-operation.${entry.operation}`)
                                    : ''}
                            </td>
                            <td>{formatDateFull(entry.changeTime)}</td>
                            <td>{entry.userName}</td>
                            <td>{entry.definition}</td>
                        </tr>
                    ))}
                </tbody>
            </Table>
        </div>
    );
};

export default PublicationLogTable;
