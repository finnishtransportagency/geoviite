import {
    AssetId,
    DesignBranch,
    JointNumber,
    KmNumber,
    LayoutDesignId,
    MainBranch,
    Oid,
    Range,
    RowVersion,
    TimeStamp,
    TrackMeter,
    TrackNumber,
} from 'common/common-model';
import {
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { RatkoPushStatus } from 'ratko/ratko-model';
import { BoundingBox, Point } from 'model/geometry';
import { LocalizationParams } from 'i18n/config';
import { SplitTargetOperation } from 'tool-panel/location-track/split-store';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

export type LayoutValidationIssue = {
    type: LayoutValidationIssueType;
    localizationKey: string;
    params: LocalizationParams;
};

export type LayoutValidationIssueType = 'FATAL' | 'ERROR' | 'WARNING';

export const validationIssueIsAtLeastAsBadAs =
    (base: LayoutValidationIssueType) =>
    (issue: LayoutValidationIssueType): boolean =>
        issue === 'FATAL'
            ? true
            : issue === 'ERROR'
            ? base !== 'FATAL'
            : issue === 'WARNING'
            ? base === 'WARNING'
            : exhaustiveMatchingGuard(issue);

export const validationIssueIsError = validationIssueIsAtLeastAsBadAs('ERROR');

export enum DraftChangeType {
    TRACK_NUMBER = 'TRACK_NUMBER',
    LOCATION_TRACK = 'LOCATION_TRACK',
    REFERENCE_LINE = 'REFERENCE_LINE',
    SWITCH = 'SWITCH',
    KM_POST = 'KM_POST',
}

export type Operation = 'CREATE' | 'DELETE' | 'MODIFY' | 'RESTORE' | 'CALCULATED';

export type PublicationGroupId = string;
export type PublicationGroup = {
    id: PublicationGroupId;
};

export enum PublicationStage {
    UNSTAGED = 'UNSTAGED',
    STAGED = 'STAGED',
}

export type PublicationId = string;

export type PublicationCandidateId =
    | LayoutTrackNumberId
    | ReferenceLineId
    | LocationTrackId
    | LayoutSwitchId
    | LayoutKmPostId;

export type BasePublicationCandidate = {
    type: DraftChangeType;
    draftChangeTime: TimeStamp;
    userName: string;
    operation: Operation;
    publicationGroup?: PublicationGroup;
    issues: LayoutValidationIssue[];
    validationState: PublicationValidationState;
    stage: PublicationStage;
    cancelled: boolean;
};

export type PublicationCandidate =
    | TrackNumberPublicationCandidate
    | LocationTrackPublicationCandidate
    | ReferenceLinePublicationCandidate
    | SwitchPublicationCandidate
    | KmPostPublicationCandidate;

export type PublicationCandidateReference =
    | {
          id: LayoutTrackNumberId;
          type: DraftChangeType.TRACK_NUMBER;
          number?: TrackNumber;
      }
    | { id: ReferenceLineId; type: DraftChangeType.REFERENCE_LINE; name?: TrackNumber }
    | { id: LocationTrackId; type: DraftChangeType.LOCATION_TRACK; name?: string }
    | { id: LayoutSwitchId; type: DraftChangeType.SWITCH; name?: string }
    | { id: LayoutKmPostId; type: DraftChangeType.KM_POST; kmNumber?: KmNumber };

export type WithBoundingBox = {
    boundingBox?: BoundingBox;
};

export type WithLocation = {
    location?: Point;
};

export type GeometryChangeRanges = {
    added: Range<number>[];
    removed: Range<number>[];
};

export type TrackNumberPublicationCandidate = BasePublicationCandidate &
    WithBoundingBox & {
        id: LayoutTrackNumberId;
        type: DraftChangeType.TRACK_NUMBER;
        number: TrackNumber;
    };

export type LocationTrackPublicationCandidate = BasePublicationCandidate &
    WithBoundingBox & {
        id: LocationTrackId;
        type: DraftChangeType.LOCATION_TRACK;
        trackNumberId: LayoutTrackNumberId;
        name: string;
        duplicateOf: LocationTrackId;
        geometryChanges?: GeometryChangeRanges;
    };

export type ReferenceLinePublicationCandidate = BasePublicationCandidate &
    WithBoundingBox & {
        id: ReferenceLineId;
        type: DraftChangeType.REFERENCE_LINE;
        trackNumberId: LayoutTrackNumberId;
        name: TrackNumber;
        operation?: Operation;
        boundingBox?: BoundingBox;
        geometryChanges?: GeometryChangeRanges;
    };

export type SwitchPublicationCandidate = BasePublicationCandidate &
    WithLocation & {
        id: LayoutSwitchId;
        type: DraftChangeType.SWITCH;
        name: string;
        trackNumberIds: LayoutTrackNumberId[];
    };

export type KmPostPublicationCandidate = BasePublicationCandidate &
    WithLocation & {
        id: LayoutKmPostId;
        type: DraftChangeType.KM_POST;
        trackNumberId: LayoutTrackNumberId;
        kmNumber: KmNumber;
        location?: Point;
    };

export type ValidatedPublicationCandidates = {
    validatedAsPublicationUnit: PublicationCandidate[];
    allChangesValidated: PublicationCandidate[];
};

export const emptyValidatedPublicationCandidates = (): ValidatedPublicationCandidates => ({
    validatedAsPublicationUnit: [],
    allChangesValidated: [],
});

export type BulkTransferState = 'PENDING' | 'CREATED' | 'IN_PROGRESS' | 'DONE' | 'FAILED';
export type SplitId = string;

export type SplitHeader = {
    id: SplitId;
    locationTrackId: LocationTrackId;
    bulkTransferState?: BulkTransferState;
    bulkTransferExpeditedStart?: boolean;
    publicationId?: PublicationId;
};

// export type SplitHeader = UnpublishedSplitHeader | PublishedSplitHeader;
//
// export type UnpublishedSplitHeader = {
//     id: SplitId;
//     locationTrackId: LocationTrackId;
// };
//
// export type PublishedSplitHeader = UnpublishedSplitHeader & {
//     bulkTransferState: BulkTransferState;
//     bulkTransferExpeditedStart: boolean;
//     publicationId: PublicationId;
// };

export type SplitTarget = {
    locationTrackId: LocationTrackId;
};

export type Split = UnpublishedSplit | PublishedSplit;

export type UnpublishedSplit = {
    id: SplitId;
    locationTrackId: LocationTrackId;
    targetLocationTracks: SplitTarget[];
    relinkedSwitches: LayoutSwitchId[];
};

type PublishedInMain = {
    branch: MainBranch;
    designBranch: undefined;
    designVersion: undefined;
};

type PublishedInDesign = {
    branch: DesignBranch;
    designBranch: LayoutDesignId;
};

export type PublishedInBranch = PublishedInMain | PublishedInDesign;

export type PublishedSplit = UnpublishedSplit & {
    publicationId: PublicationId;
    bulkTransfer: BulkTransfer;
};

export type BulkTransfer = {
    splitId: SplitId;
    expeditedStart: boolean;
    temporaryFailure: boolean;
    state: BulkTransferState;
    ratkoStartTime?: TimeStamp;
    ratkoEndTime?: TimeStamp;
    assetsTotal?: number;
    assetsMoved?: number;
    trexAssetsTotal?: number;
    trexAssetsRemaining?: number;
};

export type PublicationDetails = {
    id: PublicationId;
    publicationTime: TimeStamp;
    publicationUser: string;
    layoutBranch: PublishedInBranch;
    trackNumbers: PublishedTrackNumber[];
    referenceLines: PublishedReferenceLine[];
    locationTracks: PublishedLocationTrack[];
    switches: PublishedSwitch[];
    kmPosts: PublishedKmPost[];
    ratkoPushStatus?: RatkoPushStatus;
    ratkoPushTime?: TimeStamp;
    calculatedChanges: PublishedCalculatedChanges;
    message?: string;
    split?: SplitHeader;
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

export type PublicationRequestIds = {
    trackNumbers: LayoutTrackNumberId[];
    referenceLines: ReferenceLineId[];
    locationTracks: LocationTrackId[];
    switches: LayoutSwitchId[];
    kmPosts: LayoutKmPostId[];
};

export type PropKey = {
    key: string;
    params: LocalizationParams;
};

export type ChangeValue = {
    oldValue?: string | boolean;
    newValue?: string | boolean;
    localizationKey?: string;
};

export type PublicationChange = {
    propKey: PropKey;
    value: ChangeValue;
    remark?: string;
    enumKey?: string;
};

export type PublicationValidationState = 'IN_PROGRESS' | 'API_CALL_OK' | 'API_CALL_ERROR';

export type ValidatedAsset<Id extends AssetId> = {
    id: Id;
    errors: LayoutValidationIssue[];
};

export type ValidatedTrackNumber = ValidatedAsset<LayoutTrackNumberId>;
export type ValidatedLocationTrack = ValidatedAsset<LocationTrackId>;
export type ValidatedReferenceLine = ValidatedAsset<ReferenceLineId>;
export type ValidatedSwitch = ValidatedAsset<LayoutSwitchId>;
export type ValidatedKmPost = ValidatedAsset<LayoutKmPostId>;

export type PublicationRequest = {
    content: PublicationRequestIds;
    message: string;
};

export interface PublicationResult {
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

export type PublicationSearch = {
    startDate: TimeStamp | undefined;
    endDate: TimeStamp | undefined;
};

export type SplitInPublication = {
    id: PublicationId;
    splitId: string;
    locationTrack: LayoutLocationTrack;
    locationTrackOid: Oid;
    targetLocationTracks: SplitTargetInPublication[];
};

export type SplitTargetInPublication = {
    id: LocationTrackId;
    name: string;
    oid: Oid;
    startAddress: TrackMeter;
    endAddress: TrackMeter;
    operation: SplitTargetOperation;
};

export type DesignRowReferrer = 'MAIN_DRAFT' | 'DESIGN_DRAFT' | 'NONE';

export type LayoutBranchType = 'MAIN' | 'DESIGN';
