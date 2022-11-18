import { KmNumber, TimeStamp, TrackNumber } from 'common/common-model';
import {
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';

export type PublishValidationError = {
    type: 'ERROR' | 'WARNING';
    localizationKey: string;
    params: string[];
};

export enum RatkoPushStatus {
    SUCCESSFUL = 'SUCCESSFUL',
    FAILED = 'FAILED',
    CONNECTION_ISSUE = 'CONNECTION_ISSUE',
    IN_PROGRESS = 'IN_PROGRESS',
}

export enum DraftChangeType {
    TRACK_NUMBER = 'TRACK_NUMBER',
    LOCATION_TRACK = 'LOCATION_TRACK',
    REFERENCE_LINE = 'REFERENCE_LINE',
    SWITCH = 'SWITCH',
    KM_POST = 'KM_POST',
}

export type RatkoPushErrorType = 'PROPERTIES' | 'GEOMETRY' | 'LOCATION' | 'STATE';
export type RatkoPushErrorOperation = 'CREATE' | 'DELETE' | 'UPDATE';

export enum RatkoAssetType {
    TRACK_NUMBER = 'TRACK_NUMBER',
    LOCATION_TRACK = 'LOCATION_TRACK',
    SWITCH = 'SWITCH',
}

export type PublicationId = string;
export type RatkoPushErrorId = string;
export type RatkoPushId = string;

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
    ratkoPushTime?: TimeStamp;
};

export type RatkoPushError = {
    id: RatkoPushErrorId;
    ratkoPushId: RatkoPushId;
    ratkoPushErrorType: RatkoPushErrorType;
    operation: RatkoPushErrorOperation;
} & RatkoPushErrorAsset;

export type RatkoPushErrorAsset =
    | RatkoPushErrorTrackNumber
    | RatkoPushErrorLocationTrack
    | RatkoPushErrorSwitch;

type RatkoPushErrorLocationTrack = {
    assetType: RatkoAssetType.LOCATION_TRACK;
    asset: LayoutLocationTrack;
};

type RatkoPushErrorTrackNumber = {
    assetType: RatkoAssetType.TRACK_NUMBER;
    asset: LayoutTrackNumber;
};

type RatkoPushErrorSwitch = {
    assetType: RatkoAssetType.SWITCH;
    asset: LayoutSwitch;
};

export function ratkoPushFailed(status: RatkoPushStatus | null) {
    return status === RatkoPushStatus.FAILED || status === RatkoPushStatus.CONNECTION_ISSUE;
}
