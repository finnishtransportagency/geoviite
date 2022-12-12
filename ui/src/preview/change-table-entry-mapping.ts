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

type TranslationFunc = (tKey: string) => string;

export const trackNumberToChangeTableEntry = (
    trackNumber: TrackNumberPublishCandidate,
    t: TranslationFunc,
) => ({
    id: trackNumber.id,
    uiName: `${t('publication-table.track-number-long')} ${trackNumber.number}`,
    name: trackNumber.number,
    userName: trackNumber.userName,
    trackNumber: trackNumber.number,
    changeTime: trackNumber.draftChangeTime,
    operation: trackNumber.operation,
});

export const referenceLineToChangeTableEntry = (
    referenceLine: ReferenceLinePublishCandidate,
    trackNumbers: LayoutTrackNumber[],
    t: TranslationFunc,
) => {
    const trackNumber = trackNumbers.find((tn) => tn.id === referenceLine.trackNumberId);
    return {
        id: referenceLine.id,
        uiName: `${t('publication-table.reference-line')} ${referenceLine.name}`,
        name: referenceLine.name,
        userName: referenceLine.userName,
        trackNumber: trackNumber ? trackNumber.number : '',
        changeTime: referenceLine.draftChangeTime,
        operation: referenceLine.operation,
    };
};

export const locationTrackToChangeTableEntry = (
    locationTrack: LocationTrackPublishCandidate,
    trackNumbers: LayoutTrackNumber[],
    t: TranslationFunc,
) => {
    const trackNumber = trackNumbers.find((tn) => tn.id === locationTrack.trackNumberId);
    return {
        id: locationTrack.id,
        uiName: `${t('publication-table.location-track')} ${locationTrack.name}`,
        name: locationTrack.name,
        userName: locationTrack.userName,
        trackNumber: trackNumber ? trackNumber.number : '',
        changeTime: locationTrack.draftChangeTime,
        operation: locationTrack.operation,
    };
};

export const switchToChangeTableEntry = (
    layoutSwitch: SwitchPublishCandidate,
    trackNumbers: LayoutTrackNumber[],
    t: TranslationFunc,
) => {
    const trackNumber = trackNumbers
        .filter((tn) => layoutSwitch.trackNumberIds.some((lstn) => lstn == tn.id))
        .sort()
        .map((tn) => tn.number)
        .join(', ');
    return {
        id: layoutSwitch.id,
        uiName: `${t('publication-table.switch')} ${layoutSwitch.name}`,
        name: layoutSwitch.name,
        userName: layoutSwitch.userName,
        trackNumber,
        changeTime: layoutSwitch.draftChangeTime,
        operation: layoutSwitch.operation,
    };
};

export const kmPostChangeTableEntry = (
    kmPost: KmPostPublishCandidate,
    trackNumbers: LayoutTrackNumber[],
    t: TranslationFunc,
) => {
    const trackNumber = trackNumbers.find((tn) => tn.id === kmPost.trackNumberId);
    return {
        id: kmPost.id,
        uiName: `${t('publication-table.km-post')} ${kmPost.kmNumber}`,
        name: kmPost.kmNumber,
        userName: kmPost.userName,
        trackNumber: trackNumber ? trackNumber.number : '',
        changeTime: kmPost.draftChangeTime,
        operation: kmPost.operation,
    };
};
