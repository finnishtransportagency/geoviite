import {
    JointNumber,
    KmNumber,
    Oid,
    RowVersion,
    TimeStamp,
    TrackMeter,
    TrackNumber,
} from 'common/common-model';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { RatkoPushStatus } from 'ratko/ratko-model';
import { Point } from 'model/geometry';

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
    allChangesValidated: PublishCandidates;
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
    calculatedChanges: PublishedCalculatedChanges;
    message: string | undefined;
};

export type PublishedTrackNumber = {
    version: RowVersion;
    id: LayoutTrackNumberId;
    number: TrackNumber;
    operation: Operation;
};

export type PublishedReferenceLine = {
    version: RowVersion;
    trackNumberId: LayoutTrackNumberId;
    operation: Operation;
    changedKmNumbers: KmNumber[];
};

export type PublishedLocationTrack = {
    version: RowVersion;
    name: string;
    trackNumberId: LayoutTrackNumberId;
    operation: Operation;
    changedKmNumbers: KmNumber[];
};

export type PublishedSwitch = {
    version: RowVersion;
    trackNumberIds: LayoutTrackNumberId[];
    name: string;
    operation: Operation;
};

export type PublishedKmPost = {
    version: RowVersion;
    trackNumberId: LayoutTrackNumberId;
    kmNumber: KmNumber;
    operation: Operation;
};

export type PublishRequestIds = {
    trackNumbers: LayoutTrackNumberId[];
    referenceLines: ReferenceLineId[];
    locationTracks: LocationTrackId[];
    switches: LayoutSwitchId[];
    kmPosts: LayoutKmPostId[];
};

export type PublishRequest = {
    content: PublishRequestIds;
    message: string;
};

export interface PublishResult {
    trackNumbers: number;
    locationTracks: number;
    referenceLines: number;
    switches: number;
    kmPosts: number;
}

export interface TrackNumberChange {
    trackNumberId: LayoutTrackNumberId;
    changedKilometers: KmNumber[];
    isStartChanged: boolean;
    isEndChanged: boolean;
}

export interface LocationTrackChange {
    locationTrackId: LocationTrackId;
    changedKilometers: KmNumber[];
    isStartChanged: boolean;
    isEndChanged: boolean;
}

export interface SwitchJointChange {
    number: JointNumber;
    isRemoved: boolean;
    address: TrackMeter;
    point: Point;
    locationTrackId: LocationTrackId;
    locationTrackExternalId: Oid | null;
    trackNumberId: LayoutTrackNumberId;
    trackNumberExternalId: Oid | null;
}

export interface SwitchChange {
    switchId: LayoutSwitchId;
    changedJoints: SwitchJointChange[];
}

export interface CalculatedChanges {
    trackNumberChanges: TrackNumberChange[];
    locationTracksChanges: LocationTrackChange[];
    switchChanges: SwitchChange[];
}

export type PublishedCalculatedChanges = {
    trackNumbers: PublishedTrackNumber[];
    locationTracks: PublishedLocationTrack[];
    switches: PublishedSwitch[];
};

export type PublicationTableRowModel = {
    id: string; //Auto generated
    name: string;
    trackNumbers: TrackNumber[];
    changedKmNumbers: KmNumber[];
    operation: Operation;
    publicationTime: TimeStamp;
    publicationUser: string;
    message: string;
    ratkoPushTime: TimeStamp;
};

export interface PublishResult {
    trackNumbers: number;
    locationTracks: number;
    referenceLines: number;
    switches: number;
    kmPosts: number;
}

export interface TrackNumberChange {
    trackNumberId: LayoutTrackNumberId;
    changedKilometers: KmNumber[];
    isStartChanged: boolean;
    isEndChanged: boolean;
}

export interface LocationTrackChange {
    locationTrackId: LocationTrackId;
    changedKilometers: KmNumber[];
    isStartChanged: boolean;
    isEndChanged: boolean;
}

export interface SwitchJointChange {
    number: JointNumber;
    isRemoved: boolean;
    address: TrackMeter;
    point: Point;
    locationTrackId: LocationTrackId;
    locationTrackExternalId: Oid | null;
    trackNumberId: LayoutTrackNumberId;
    trackNumberExternalId: Oid | null;
}

export interface SwitchChange {
    switchId: LayoutSwitchId;
    changedJoints: SwitchJointChange[];
}

export interface CalculatedChanges {
    trackNumberChanges: TrackNumberChange[];
    locationTracksChanges: LocationTrackChange[];
    switchChanges: SwitchChange[];
}
