import { KmNumber, RowVersion, TimeStamp, TrackNumber } from 'common/common-model';
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

export type Operation = 'CREATE' | 'DELETE' | 'MODIFY' | 'RESTORE';

export type PublicationId = string;
export type PublicationUserName = string;

export type PublishCandidate = {
    draftChangeTime: TimeStamp;
    userName: string;
    operation: Operation;
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
    trackNumberId: LayoutTrackNumberId;
    name: string;
    duplicateOf: LocationTrackId;
};

export type ReferenceLinePublishCandidate = PublishCandidate & {
    type: DraftChangeType.REFERENCE_LINE;
    id: ReferenceLineId;
    trackNumberId: LayoutTrackNumberId;
    name: TrackNumber;
    operation: Operation | null;
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
};

export type PublicationDetails = {
    id: PublicationId;
    publicationTime: TimeStamp;
    publicationUser: string;
    trackNumbers: PublishedTrackNumber[];
    referenceLines: PublishedReferenceLine[];
    locationTracks: PublishedLocationTrack[];
    switches: PublishedSwitch[];
    kmPosts: PublishedKmPost[];
    ratkoPushStatus: RatkoPushStatus | null;
    ratkoPushTime: TimeStamp | null;
};

export type PublishedTrackNumber = {
    id: PublicationId;
    version: RowVersion;
    number: TrackNumber;
    operation: Operation;
};

export type PublishedReferenceLine = {
    id: PublicationId;
    version: RowVersion;
    trackNumberId: LayoutTrackNumberId;
    operation: Operation;
};

export type PublishedLocationTrack = {
    id: PublicationId;
    version: RowVersion;
    name: string;
    trackNumberId: LayoutTrackNumberId;
    operation: Operation;
};

export type PublishedSwitch = {
    id: PublicationId;
    version: RowVersion;
    trackNumberIds: LayoutTrackNumberId[];
    name: string;
    operation: Operation;
};

export type PublishedKmPost = {
    id: PublicationId;
    version: RowVersion;
    trackNumberId: LayoutTrackNumberId;
    kmNumber: KmNumber;
    operation: Operation;
};
