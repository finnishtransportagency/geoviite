import { KmNumber, TimeStamp, TrackNumber } from 'common/common-model';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { RatkoPushStatus } from 'ratko/ratko-model';

export type PublishValidationError = {
    type: 'ERROR' | 'WARNING';
    localizationKey: string;
    params: string[];
};

export enum DraftChangeType {
    TRACK_NUMBER = 'TRACK_NUMBER',
    LOCATION_TRACK = 'LOCATION_TRACK',
    REFERENCE_LINE = 'REFERENCE_LINE',
    SWITCH = 'SWITCH',
    KM_POST = 'KM_POST',
}

export type PublicationId = string;

export type PublishCandidate = {
    draftChangeTime: TimeStamp;
    errors: PublishValidationError[];
};

export type TrackNumberPublishCandidate = PublishCandidate & {
    type: DraftChangeType.TRACK_NUMBER;
    number: TrackNumber;
    id: LayoutTrackNumberId;
};

export type LocationTrackPublishCandidate = PublishCandidate & {
    type: DraftChangeType.LOCATION_TRACK;
    id: LocationTrackId;
    trackNumberId: LayoutTrackNumberId | null;
    name: string;
};

export type ReferenceLinePublishCandidate = PublishCandidate & {
    type: DraftChangeType.REFERENCE_LINE;
    id: ReferenceLineId;
    trackNumberId: LayoutTrackNumberId;
    name: string;
};

export type SwitchPublishCandidate = PublishCandidate & {
    type: DraftChangeType.SWITCH;
    id: LayoutSwitchId;
    name: string;
    trackNumberIds: LayoutTrackNumberId[];
};

export type KmPostPublishCandidate = PublishCandidate & {
    type: DraftChangeType.KM_POST;
    id: LayoutKmPostId;
    trackNumberId: LayoutTrackNumberId;
    kmNumber: KmNumber;
};

export type PublishCandidates = {
    trackNumbers: TrackNumberPublishCandidate[];
    locationTracks: LocationTrackPublishCandidate[];
    referenceLines: ReferenceLinePublishCandidate[];
    switches: SwitchPublishCandidate[];
    kmPosts: KmPostPublishCandidate[];
};

export type ValidatedPublishCandidates = {
    validatedAsPublicationUnit: PublishCandidates;
    validatedSeparately: PublishCandidates;
}

export type PublicationListingItem = {
    id: PublicationId;
    publishTime: TimeStamp;
    ratkoPushTime?: TimeStamp;
    status: RatkoPushStatus | null;
    trackNumberIds: LayoutTrackNumberId[];
    hasRatkoPushError: boolean;
};

export type PublicationDetails = {
    id: PublicationId;
    publishTime: TimeStamp;
    trackNumbers: TrackNumberPublishCandidate[];
    referenceLines: ReferenceLinePublishCandidate[];
    locationTracks: LocationTrackPublishCandidate[];
    switches: SwitchPublishCandidate[];
    kmPosts: KmPostPublishCandidate[];
    status: RatkoPushStatus | null;
    ratkoPushTime?: TimeStamp | null;
};
