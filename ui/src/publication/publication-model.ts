import {
    AssetId,
    JointNumber,
    KmNumber,
    Oid,
    Range,
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
import { BoundingBox, Point } from 'model/geometry';

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

export type Operation = 'CREATE' | 'DELETE' | 'MODIFY' | 'RESTORE' | 'CALCULATED';

export type PublicationId = string;

export type PublishCandidate = {
    draftChangeTime: TimeStamp;
    userName: string;
    operation: Operation;
    errors: PublishValidationError[];
};

export type WithBoundingBox = {
    boundingBox?: BoundingBox;
};

export type WithLocation = {
    location?: Point;
};

export type TrackNumberPublishCandidate = PublishCandidate &
    WithBoundingBox & {
        type: DraftChangeType.TRACK_NUMBER;
        number: TrackNumber;
        id: LayoutTrackNumberId;
    };

export type LocationTrackPublishCandidate = PublishCandidate &
    WithBoundingBox & {
        type: DraftChangeType.LOCATION_TRACK;
        id: LocationTrackId;
        trackNumberId: LayoutTrackNumberId;
        name: string;
        duplicateOf: LocationTrackId;
    };

export type ReferenceLinePublishCandidate = PublishCandidate &
    WithBoundingBox & {
        type: DraftChangeType.REFERENCE_LINE;
        id: ReferenceLineId;
        trackNumberId: LayoutTrackNumberId;
        name: TrackNumber;
        operation?: Operation;
        boundingBox?: BoundingBox;
    };

export type SwitchPublishCandidate = PublishCandidate &
    WithLocation & {
        type: DraftChangeType.SWITCH;
        id: LayoutSwitchId;
        name: string;
        trackNumberIds: LayoutTrackNumberId[];
    };

export type KmPostPublishCandidate = PublishCandidate &
    WithLocation & {
        type: DraftChangeType.KM_POST;
        id: LayoutKmPostId;
        trackNumberId: LayoutTrackNumberId;
        kmNumber: KmNumber;
        location?: Point;
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
    ratkoPushStatus?: RatkoPushStatus;
    ratkoPushTime?: TimeStamp;
    calculatedChanges: PublishedCalculatedChanges;
    message?: string;
};

export type PublishedTrackNumber = {
    version: RowVersion;
    id: LayoutTrackNumberId;
    number: TrackNumber;
    operation: Operation;
    changedKmNumbers: KmNumber[];
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

export type PropKey = {
    key: string;
    params: string[];
};

export type ChangeValue = {
    oldValue?: string | boolean;
    newValue?: string | boolean;
    localizationKey?: string;
};

export type PublicationChange = {
    propKey: PropKey;
    value: ChangeValue;
    remark?: PublicationChangeRemark;
    enumKey?: string;
};

export type PublicationChangeRemark = {
    key: string;
    value: string;
};

export type ValidatedAsset = {
    id: AssetId;
    errors: PublishValidationError[];
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
    locationTrackExternalId?: Oid;
    trackNumberId: LayoutTrackNumberId;
    trackNumberExternalId?: Oid;
}

export interface SwitchChange {
    switchId: LayoutSwitchId;
    changedJoints: SwitchJointChange[];
}

export interface DirectChanges {
    kmPostChanges: LayoutKmPostId[];
    referenceLineChanges: ReferenceLineId[];
    trackNumberChanges: TrackNumberChange[];
    locationTrackChanges: LocationTrackChange[];
    switchChanges: SwitchChange[];
}

export interface IndirectChanges {
    trackNumberChanges: TrackNumberChange[];
    locationTrackChanges: LocationTrackChange[];
    switchChanges: SwitchChange[];
}

export interface CalculatedChanges {
    directChanges: DirectChanges;
    indirectChanges: IndirectChanges;
}

export type PublishedCalculatedChanges = {
    trackNumbers: PublishedTrackNumber[];
    locationTracks: PublishedLocationTrack[];
    switches: PublishedSwitch[];
};

export type PublicationTableItem = {
    id: string; //Auto generated
    name: string;
    trackNumbers: TrackNumber[];
    changedKmNumbers: Range<string>[];
    operation: Operation;
    publicationTime: TimeStamp;
    publicationUser: string;
    message: string;
    ratkoPushTime: TimeStamp;
    propChanges: PublicationChange[];
};
