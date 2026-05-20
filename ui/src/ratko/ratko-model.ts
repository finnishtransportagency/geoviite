import {
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';

export enum RatkoPushStatus {
    SUCCESSFUL = 'SUCCESSFUL',
    FAILED = 'FAILED',
    CONNECTION_ISSUE = 'CONNECTION_ISSUE',
    IN_PROGRESS = 'IN_PROGRESS',
    IN_PROGRESS_M_VALUES = 'IN_PROGRESS_M_VALUES',
    MANUAL_RETRY = 'MANUAL_RETRY',
}

export function ratkoPushFailed(status: RatkoPushStatus | undefined) {
    return status === RatkoPushStatus.FAILED || status === RatkoPushStatus.CONNECTION_ISSUE;
}

export function ratkoPushInProgress(status: RatkoPushStatus | undefined) {
    return (
        status === RatkoPushStatus.IN_PROGRESS || status === RatkoPushStatus.IN_PROGRESS_M_VALUES
    );
}

export const ratkoPushSucceeded = (status: RatkoPushStatus | undefined) =>
    status === RatkoPushStatus.SUCCESSFUL;

export type RatkoPushErrorType = 'PROPERTIES' | 'GEOMETRY' | 'LOCATION' | 'STATE' | 'INTERNAL';

export type RatkoPushErrorOperation = 'CREATE' | 'DELETE' | 'UPDATE' | 'FETCH_EXISTING';

export enum RatkoAssetType {
    TRACK_NUMBER = 'TRACK_NUMBER',
    LOCATION_TRACK = 'LOCATION_TRACK',
    SWITCH = 'SWITCH',
}

export type RatkoLocationTrackRef = {
    type: RatkoAssetType.LOCATION_TRACK;
    id: LocationTrackId;
    oid?: string;
};

export type RatkoTrackNumberRef = {
    type: RatkoAssetType.TRACK_NUMBER;
    id: LayoutTrackNumberId;
    oid?: string;
};

export type RatkoSwitchRef = {
    type: RatkoAssetType.SWITCH;
    id: LayoutSwitchId;
    oid?: string;
};

export type RatkoAssetRef = RatkoLocationTrackRef | RatkoTrackNumberRef | RatkoSwitchRef;

export type RatkoPushErrorId = string;

export type RatkoPushId = string;

type RatkoPushErrorBase = {
    id: RatkoPushErrorId;
    ratkoPushId: RatkoPushId;
    errorType: RatkoPushErrorType;
    ratkoStatusCode?: string;
    technicalMessage: string;
};

export type RatkoPushAssetError = RatkoPushErrorBase & {
    operation: RatkoPushErrorOperation;
    assetRef: RatkoAssetRef;
};

export type RatkoPushError = RatkoPushErrorBase | RatkoPushAssetError;

export function isAssetError(error: RatkoPushError): error is RatkoPushAssetError {
    return 'assetRef' in error;
}
