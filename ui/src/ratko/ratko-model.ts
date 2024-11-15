import {
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
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

export type RatkoPushErrorId = string;

export type RatkoPushId = string;

export type RatkoPushError = {
    id: RatkoPushErrorId;
    ratkoPushId: RatkoPushId;
    errorType: RatkoPushErrorType;
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
