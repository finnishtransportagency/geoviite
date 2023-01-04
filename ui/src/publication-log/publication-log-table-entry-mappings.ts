// todo move these helper functions to a separate file
import {
    PublishedKmPost,
    PublishedLocationTrack,
    PublishedReferenceLine,
    PublishedSwitch,
    PublishedTrackNumber,
    SwitchPublishCandidate,
} from 'publication/publication-model';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { PublicationLogTableEntry } from 'publication-log/publication-log-table';

const publicationLogTableEntryCommonFields = (
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

export const trackNumberToLogTableEntry = (
    trackNumber: PublishedTrackNumber,
    changeTime: string,
    userName: string,
): PublicationLogTableEntry => {
    return {
        ...publicationLogTableEntryCommonFields(trackNumber, changeTime, userName),
        name: trackNumber.number,
        trackNumber: trackNumber.number,
        changedKmNumbers: '', // todo tulossa myöhemmin
        definition: '', // todo tulossa myöhemmin
    };
};

export const kmPostToLogTableEntry = (
    kmPost: PublishedKmPost,
    changeTime: string,
    userName: string,
    trackNumbers: LayoutTrackNumber[],
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => tn.id === kmPost.trackNumberId);
    return {
        ...publicationLogTableEntryCommonFields(kmPost, changeTime, userName),
        name: kmPost.kmNumber,
        trackNumber: trackNumber ? trackNumber.number : '',
        changedKmNumbers: '', // todo tulossa myöhemmin
        definition: '', // todo tulossa myöhemmin
    };
};

export const locationTrackToLogTableEntry = (
    locationTrack: PublishedLocationTrack,
    changeTime: string,
    userName: string,
    trackNumbers: LayoutTrackNumber[],
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => tn.id === locationTrack.trackNumberId);
    return {
        ...publicationLogTableEntryCommonFields(locationTrack, changeTime, userName),
        name: locationTrack.name,
        trackNumber: trackNumber ? trackNumber.number : '',
        changedKmNumbers: '', // todo tulossa myöhemmin
        definition: '', // todo tulossa myöhemmin
    };
};

export const referenceLineToLogTableEntry = (
    referenceLine: PublishedReferenceLine,
    changeTime: string,
    userName: string,
    trackNumbers: LayoutTrackNumber[],
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => tn.id === referenceLine.trackNumberId);
    return {
        ...publicationLogTableEntryCommonFields(referenceLine, changeTime, userName),
        name: referenceLine.trackNumberId,
        trackNumber: trackNumber ? trackNumber.number : '',
        changedKmNumbers: '', // todo tulossa myöhemmin
        definition: '', // todo tulossa myöhemmin
    };
};

export const switchesToLogTableEntry = (
    publishedSwitch: PublishedSwitch,
    changeTime: string,
    userName: string,
    trackNumbers: LayoutTrackNumber[],
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => publishedSwitch.trackNumberIds.includes(tn.id));
    return {
        ...publicationLogTableEntryCommonFields(publishedSwitch, changeTime, userName),
        name: publishedSwitch.name,
        trackNumber: trackNumber ? trackNumber.number : '',
        changedKmNumbers: '', // todo tulossa myöhemmin
        definition: '', // todo tulossa myöhemmin
    };
};
