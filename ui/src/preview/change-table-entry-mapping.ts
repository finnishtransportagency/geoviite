import {
    KmPostPublishCandidate,
    LocationTrackPublishCandidate,
    Operation,
    ReferenceLinePublishCandidate,
    SwitchPublishCandidate,
    TrackNumberPublishCandidate,
} from 'publication/publication-model';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import {
    getKmPostUiName,
    getLocationTrackUiName,
    getReferenceLineUiName,
    getSwitchUiName,
    getTrackNumberUiName,
} from 'publication/table/publication-table-utils';

type PublicationId =
    | LayoutTrackNumberId
    | ReferenceLineId
    | LocationTrackId
    | LayoutSwitchId
    | LayoutKmPostId;

export type ChangeTableEntry = {
    id: PublicationId;
    name: string;
    uiName: string;
    trackNumber: string;
    changeTime: string;
    userName: string;
    operation: Operation;
};

const changeTableEntryCommonFields = (
    candidate:
        | LocationTrackPublishCandidate
        | TrackNumberPublishCandidate
        | ReferenceLinePublishCandidate
        | SwitchPublishCandidate
        | KmPostPublishCandidate,
) => ({
    id: candidate.id,
    userName: candidate.userName,
    changeTime: candidate.draftChangeTime,
    operation: candidate.operation,
});

export const trackNumberToChangeTableEntry = (trackNumber: TrackNumberPublishCandidate) => ({
    ...changeTableEntryCommonFields(trackNumber),
    uiName: getTrackNumberUiName(trackNumber.number),
    name: trackNumber.number,
    trackNumber: trackNumber.number,
});

export const referenceLineToChangeTableEntry = (
    referenceLine: ReferenceLinePublishCandidate,
    trackNumbers: LayoutTrackNumber[],
) => {
    const trackNumber = trackNumbers.find((tn) => tn.id === referenceLine.trackNumberId);
    return {
        ...changeTableEntryCommonFields(referenceLine),
        uiName: getReferenceLineUiName(referenceLine.name),
        name: referenceLine.name,
        trackNumber: trackNumber ? trackNumber.number : '',
    };
};

export const locationTrackToChangeTableEntry = (
    locationTrack: LocationTrackPublishCandidate,
    trackNumbers: LayoutTrackNumber[],
) => {
    const trackNumber = trackNumbers.find((tn) => tn.id === locationTrack.trackNumberId);
    return {
        ...changeTableEntryCommonFields(locationTrack),
        uiName: getLocationTrackUiName(locationTrack.name),
        name: locationTrack.name,
        trackNumber: trackNumber ? trackNumber.number : '',
    };
};

export const switchToChangeTableEntry = (
    layoutSwitch: SwitchPublishCandidate,
    trackNumbers: LayoutTrackNumber[],
) => {
    const trackNumber = trackNumbers
        .filter((tn) => layoutSwitch.trackNumberIds.some((lstn) => lstn == tn.id))
        .sort()
        .map((tn) => tn.number)
        .join(', ');
    return {
        ...changeTableEntryCommonFields(layoutSwitch),
        uiName: getSwitchUiName(layoutSwitch.name),
        name: layoutSwitch.name,
        trackNumber,
    };
};

export const kmPostChangeTableEntry = (
    kmPost: KmPostPublishCandidate,
    trackNumbers: LayoutTrackNumber[],
) => {
    const trackNumber = trackNumbers.find((tn) => tn.id === kmPost.trackNumberId);
    return {
        ...changeTableEntryCommonFields(kmPost),
        uiName: getKmPostUiName(kmPost.kmNumber),
        name: kmPost.kmNumber,
        trackNumber: trackNumber ? trackNumber.number : '',
    };
};
