import { Table, Th } from 'vayla-design-lib/table/table';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './publication-log.scss';
import {
    KmPostPublishCandidate,
    LocationTrackPublishCandidate,
    Operation,
    PublicationDetails,
    ReferenceLinePublishCandidate,
    SwitchPublishCandidate,
    TrackNumberPublishCandidate,
} from 'publication/publication-model';
import { ChangeTableEntry } from 'preview/change-table-entry-mapping';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';

// datepicker component

// operation muutos

// {id: PublicationId,
//     name: string,
//     uiName: string,
//     trackNumber: string,
//     changeTime: string,
//     userName: string,
//     operation: Operation}

type PublicationId =
    | LayoutTrackNumberId
    | ReferenceLineId
    | LocationTrackId
    | LayoutSwitchId
    | LayoutKmPostId;

export type PublicationLogTableEntry = {
    id: PublicationId;
    name: string;
    trackNumber: string;
    operation: Operation;
    changeTime: string;
    userName: string;
    kilometers: string;
    definition: string;
};

const changeTableEntryCommonFields = (
    candidate:
        | LocationTrackPublishCandidate
        | TrackNumberPublishCandidate
        | ReferenceLinePublishCandidate
        | SwitchPublishCandidate
        | KmPostPublishCandidate,
    changeTime: string,
    userName: string,
) => ({
    id: candidate.id,
    operation: candidate.operation,
    changeTime: changeTime,
    userName: userName,
});

const trackNumberToLogTableEntry = (
    trackNumber: TrackNumberPublishCandidate,
    changeTime: string,
    userName: string,
): PublicationLogTableEntry => ({
    ...changeTableEntryCommonFields(trackNumber, changeTime, userName),
    name: trackNumber.number,
    trackNumber: trackNumber.number,
    kilometers: '', // ????
    definition: '',
});

const kmPostToLogTableEntry = (
    kmPost: KmPostPublishCandidate,
    changeTime: string,
    userName: string,
    trackNumbers: LayoutTrackNumber[],
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => tn.id === kmPost.trackNumberId);
    return {
        ...changeTableEntryCommonFields(kmPost, changeTime, userName),
        name: kmPost.kmNumber,
        trackNumber: trackNumber ? trackNumber.number : '',
        kilometers: '', // ????
        definition: '',
    };
};

const PublicationLogTable: React.FC = () => {
    const { t } = useTranslation();

    const [trackNumbers, setTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);
    React.useEffect(() => {
        getTrackNumbers('OFFICIAL').then((trackNumbers) => setTrackNumbers(trackNumbers));
    }, []);

    const publicationLogTableEntries: PublicationLogTableEntry[] = [];

    const detailsToEntry = (publicationDetails: PublicationDetails) => {
        const changeTime = publicationDetails.publicationTime;
        const userName = publicationDetails.publicationUser;
        const trackNums = publicationDetails.trackNumbers.map((t) =>
            trackNumberToLogTableEntry(t, changeTime, userName),
        );
        const kmPosts = publicationDetails.kmPosts.map((km) =>
            kmPostToLogTableEntry(km, changeTime, userName, trackNumbers),
        );

        // todo
        publicationDetails.locationTracks;
        publicationDetails.referenceLines;
        publicationDetails.switches;
    };

    // todo add datepicker to get start and end dates

    // todo add fetch PublicationDetails

    return (
        <div className={styles['publication-table__container']}>
            <Table wide>
                <thead className={styles['publication-table__header']}>
                    <tr>
                        <Th>{t('publication-log-table.change-target')}</Th>
                        <Th>{t('publication-log-table.track-number-short')}</Th>
                        <Th>{t('publication-log-table.kilometers')}</Th>
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
                                <td>{entry.kilometers}</td>
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
