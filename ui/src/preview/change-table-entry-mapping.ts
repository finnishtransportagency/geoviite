import {
    KmPostPublicationCandidate,
    LocationTrackPublicationCandidate,
    Operation,
    PublicationGroup,
    ReferenceLinePublicationCandidate,
    SwitchPublicationCandidate,
    TrackNumberPublicationCandidate,
} from 'publication/publication-model';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { KmNumber, TrackNumber } from 'common/common-model';
import i18n from 'i18next';

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
    publicationGroup?: PublicationGroup;
};

const changeTableEntryCommonFields = (
    candidate:
        | LocationTrackPublicationCandidate
        | TrackNumberPublicationCandidate
        | ReferenceLinePublicationCandidate
        | SwitchPublicationCandidate
        | KmPostPublicationCandidate,
) => ({
    id: candidate.id,
    userName: candidate.userName,
    changeTime: candidate.draftChangeTime,
    operation: candidate.operation,
    publicationGroup: candidate.publicationGroup,
});

export function getTrackNumberUiName(trackNumber: TrackNumber | undefined) {
    return `${i18n.t('publication-table.track-number-long')} ${trackNumber}`;
}

export function getReferenceLineUiName(trackNumber: TrackNumber | undefined) {
    return `${i18n.t('publication-table.reference-line')} ${trackNumber}`;
}

export function getLocationTrackUiName(name: string) {
    return `${i18n.t('publication-table.location-track')} ${name}`;
}

export function getSwitchUiName(name: string) {
    return `${i18n.t('publication-table.switch')} ${name}`;
}

export function getKmPostUiName(kmNumber: KmNumber) {
    return `${i18n.t('publication-table.km-post')} ${kmNumber}`;
}

export const trackNumberToChangeTableEntry = (trackNumber: TrackNumberPublicationCandidate) => ({
    ...changeTableEntryCommonFields(trackNumber),
    uiName: getTrackNumberUiName(trackNumber.number),
    name: trackNumber.number,
    trackNumber: trackNumber.number,
});

export const referenceLineToChangeTableEntry = (
    referenceLine: ReferenceLinePublicationCandidate,
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
    locationTrack: LocationTrackPublicationCandidate,
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
    layoutSwitch: SwitchPublicationCandidate,
    trackNumbers: LayoutTrackNumber[],
) => {
    const trackNumber = trackNumbers
        .filter((tn) => layoutSwitch.trackNumberIds.some((lstn) => lstn === tn.id))
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
    kmPost: KmPostPublicationCandidate,
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
