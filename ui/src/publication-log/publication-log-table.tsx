import { Table, Th } from 'vayla-design-lib/table/table';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './publication-log.scss';
import {
    LocationTrackPublishCandidate,
    Operation,
    PublicationDetails,
    PublicationId,
    PublishedKmPost,
    PublishedLocationTrack,
    PublishedReferenceLine,
    PublishedSwitch,
    PublishedTrackNumber,
    ReferenceLinePublishCandidate,
    SwitchPublishCandidate,
} from 'publication/publication-model';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { useLoader } from 'utils/react-utils';
import { getPublications, getPublicationsForFrontpage } from 'publication/publication-api';

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

// todo move these helper functions to a separate file
const changeTableEntryCommonFields = (
    candidate:
        | PublishedLocationTrack
        | PublishedTrackNumber
        | PublishedReferenceLine
        | SwitchPublishCandidate
        | PublishedKmPost
        | PublishedSwitch,
    changeTime: string,
    userName: string,
) => ({
    id: candidate.id,
    operation: candidate.operation,
    changeTime: changeTime,
    userName: userName,
});

const trackNumberToLogTableEntry = (
    trackNumber: PublishedTrackNumber,
    changeTime: string,
    userName: string,
): PublicationLogTableEntry => ({
    ...changeTableEntryCommonFields(trackNumber, changeTime, userName),
    name: trackNumber.number,
    trackNumber: trackNumber.number,
    changedKmNumbers: '', // todo tulossa myöhemmin
    definition: '', // todo tulossa myöhemmin
});

const kmPostToLogTableEntry = (
    kmPost: PublishedKmPost,
    changeTime: string,
    userName: string,
    trackNumbers: LayoutTrackNumber[],
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => tn.id === kmPost.trackNumberId);
    return {
        ...changeTableEntryCommonFields(kmPost, changeTime, userName),
        name: kmPost.kmNumber,
        trackNumber: trackNumber ? trackNumber.number : '',
        changedKmNumbers: '', // todo tulossa myöhemmin
        definition: '', // todo tulossa myöhemmin
    };
};

const locationTrackToLogTableEntry = (
    locationTrack: PublishedLocationTrack,
    changeTime: string,
    userName: string,
    trackNumbers: LayoutTrackNumber[],
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => tn.id === locationTrack.trackNumberId);
    return {
        ...changeTableEntryCommonFields(locationTrack, changeTime, userName),
        name: locationTrack.name,
        trackNumber: trackNumber ? trackNumber.number : '',
        changedKmNumbers: '', // todo tulossa myöhemmin
        definition: '', // todo tulossa myöhemmin
    };
};

const referenceLineToLogTableEntry = (
    referenceLine: PublishedReferenceLine,
    changeTime: string,
    userName: string,
    trackNumbers: LayoutTrackNumber[],
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => tn.id === referenceLine.trackNumberId);
    return {
        ...changeTableEntryCommonFields(referenceLine, changeTime, userName),
        name: referenceLine.trackNumberId,
        trackNumber: trackNumber ? trackNumber.number : '',
        changedKmNumbers: '', // todo tulossa myöhemmin
        definition: '', // todo tulossa myöhemmin
    };
};

const switchesToLogTableEntry = (
    publishedSwitch: PublishedSwitch,
    changeTime: string,
    userName: string,
    trackNumbers: LayoutTrackNumber[],
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => publishedSwitch.trackNumberIds.includes(tn.id));
    return {
        ...changeTableEntryCommonFields(publishedSwitch, changeTime, userName),
        name: publishedSwitch.name,
        trackNumber: trackNumber ? trackNumber.number : '',
        changedKmNumbers: '', // todo tulossa myöhemmin
        definition: '', // todo tulossa myöhemmin
    };
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

        const trackNums: PublicationLogTableEntry[] = publicationDetails.trackNumbers.map((t) =>
            trackNumberToLogTableEntry(t, changeTime, userName),
        );
        const kmPosts: PublicationLogTableEntry[] = publicationDetails.kmPosts.map((km) =>
            kmPostToLogTableEntry(km, changeTime, userName, trackNumbers),
        );
        const lTracks: PublicationLogTableEntry[] = publicationDetails.locationTracks.map((l) =>
            locationTrackToLogTableEntry(l, changeTime, userName, trackNumbers),
        );
        const rLines = publicationDetails.referenceLines.map((rl) =>
            referenceLineToLogTableEntry(rl, changeTime, userName, trackNumbers),
        );
        const switches = publicationDetails.switches.map((s) =>
            switchesToLogTableEntry(s, changeTime, userName, trackNumbers),
        );

        return trackNums.concat(kmPosts).concat(lTracks).concat(rLines).concat(switches);
    };

    // todo add fetch PublicationDetails with dates
    const publicationDetails = useLoader(
        () => getPublications(startDate, endDate),
        [startDate, endDate],
    );

    const publicationLogTableEntries: PublicationLogTableEntry[] = publicationDetails
        ? detailsToEntry(publicationDetails)
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
                    {publicationLogTableEntries.map((entry) => (
                        <React.Fragment key={entry.id}>
                            <tr className={'preview-table-item'}>
                                <td>{entry.name}</td>
                                <td>{entry.trackNumber}</td>
                                <td>{entry.changedKmNumbers}</td>
                                <td>{entry.operation}</td>
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
