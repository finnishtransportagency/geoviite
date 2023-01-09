import {
    Operation,
    PublicationDetails,
    PublicationId,
    PublishedKmPost,
    PublishedLocationTrack,
    PublishedReferenceLine,
    PublishedSwitch,
    PublishedTrackNumber,
} from 'publication/publication-model';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import {
    getKmPostUiName,
    getLocationTrackUiName,
    getReferenceLineUiName,
    getSwitchUiName,
    getTrackNumberUiName,
} from 'preview/change-table-entry-mapping';
import { KmNumber, TimeStamp, TrackNumber } from 'common/common-model';

export type PublicationLogTableCommonFields = {
    publicationId: PublicationId;
    changeTime: TimeStamp;
    userName: string;
    definition: string;
};

export type PublicationLogTableEntry = {
    name: string;
    trackNumbers: TrackNumber[];
    operation?: Operation;
    changedKmNumbers: KmNumber[];
} & PublicationLogTableCommonFields;

export const getPublicationLogTableEntryCommonFields = (
    publication: PublicationDetails,
): PublicationLogTableCommonFields => ({
    publicationId: publication.id,
    changeTime: publication.publicationTime,
    userName: publication.publicationUser,
    definition: '', //todo tulossa myöhemmin
});

export const trackNumberToLogTableEntry = (
    trackNumber: PublishedTrackNumber,
    trackNumbers: LayoutTrackNumber[],
    commonFields: PublicationLogTableCommonFields,
): PublicationLogTableEntry => {
    const tn = trackNumbers.find((tn) => tn.id === trackNumber.id);

    return {
        ...commonFields,
        name: tn ? getTrackNumberUiName(tn.number) : '',
        trackNumbers: tn ? [tn.number] : [],
        operation: trackNumber.operation,
        changedKmNumbers: [], // todo tulossa myöhemmin
    };
};

export const kmPostToLogTableEntry = (
    kmPost: PublishedKmPost,
    trackNumbers: LayoutTrackNumber[],
    commonFields: PublicationLogTableCommonFields,
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => tn.id === kmPost.trackNumberId);
    return {
        ...commonFields,
        name: getKmPostUiName(kmPost.kmNumber),
        trackNumbers: trackNumber ? [trackNumber.number] : [],
        operation: kmPost.operation,
        changedKmNumbers: [], // todo tulossa myöhemmin
    };
};

export const locationTrackToLogTableEntry = (
    locationTrack: PublishedLocationTrack,
    trackNumbers: LayoutTrackNumber[],
    commonFields: PublicationLogTableCommonFields,
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => tn.id === locationTrack.trackNumberId);
    return {
        ...commonFields,
        name: getLocationTrackUiName(locationTrack.name),
        trackNumbers: trackNumber ? [trackNumber.number] : [],
        operation: locationTrack.operation,
        changedKmNumbers: [], // todo tulossa myöhemmin
    };
};

export const referenceLineToLogTableEntry = (
    referenceLine: PublishedReferenceLine,
    trackNumbers: LayoutTrackNumber[],
    commonFields: PublicationLogTableCommonFields,
): PublicationLogTableEntry => {
    const trackNumber = trackNumbers.find((tn) => tn.id === referenceLine.trackNumberId);
    return {
        ...commonFields,
        name: getReferenceLineUiName(trackNumber?.number),
        trackNumbers: trackNumber ? [trackNumber.number] : [],
        changedKmNumbers: [], // todo tulossa myöhemmin
    };
};

export const switchesToLogTableEntry = (
    publishedSwitch: PublishedSwitch,
    trackNumbers: LayoutTrackNumber[],
    commonFields: PublicationLogTableCommonFields,
): PublicationLogTableEntry => {
    const tNumbers = trackNumbers.filter((tn) => publishedSwitch.trackNumberIds.includes(tn.id));

    return {
        ...commonFields,
        name: getSwitchUiName(publishedSwitch.name),
        trackNumbers: tNumbers.map((tn) => tn.number),
        operation: publishedSwitch.operation,
        changedKmNumbers: [], // todo tulossa myöhemmin
    };
};
