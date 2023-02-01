import {
    API_URI,
    deleteAdt,
    getIgnoreError,
    postAdt,
    postIgnoreError,
    queryParams,
} from 'api/api-fetch';
import { JointNumber, KmNumber, Oid, TrackMeter } from 'common/common-model';
import {
    PublicationDetails,
    PublicationId,
    PublishCandidates,
    ValidatedPublishCandidates,
} from 'publication/publication-model';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';
import { formatISODate } from 'utils/date-utils';

const PUBLICATION_URL = `${API_URI}/publications`;

export type PublishRequestIds = {
    trackNumbers: LayoutTrackNumberId[];
    referenceLines: ReferenceLineId[];
    locationTracks: LocationTrackId[];
    switches: LayoutSwitchId[];
    kmPosts: LayoutKmPostId[];
};

export type PublishRequest = {
    message: string;
} & PublishRequestIds;

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

export const getPublishCandidates = () =>
    getIgnoreError<PublishCandidates>(`${PUBLICATION_URL}/candidates`);

export const validatePublishCandidates = (request: PublishRequestIds) =>
    postIgnoreError<PublishRequestIds, ValidatedPublishCandidates>(
        `${PUBLICATION_URL}/validate`,
        request,
    );

export const revertCandidates = (request: PublishRequestIds) =>
    deleteAdt<PublishRequestIds, PublishResult>(`${PUBLICATION_URL}/candidates`, request, true);

export const publishCandidates = (request: PublishRequest) => {
    return postAdt<PublishRequest, PublishResult>(`${PUBLICATION_URL}`, request, true);
};

export const getPublications = (fromDate?: Date, toDate?: Date) => {
    const params = queryParams({
        from: fromDate ? formatISODate(fromDate) : '',
        to: toDate ? formatISODate(toDate) : '',
    });

    return getIgnoreError<PublicationDetails[]>(`${PUBLICATION_URL}${params}`);
};

export const publicationsCsvUri = `${PUBLICATION_URL}/csv`;

export const getPublication = (id: PublicationId) =>
    getIgnoreError<PublicationDetails>(`${PUBLICATION_URL}/${id}`);

export const getCalculatedChanges = (request: PublishRequestIds) =>
    postIgnoreError<PublishRequestIds, CalculatedChanges>(
        `${PUBLICATION_URL}/calculated-changes`,
        request,
    );

export const getRevertRequestDependencies = (request: PublishRequestIds) =>
    postIgnoreError<PublishRequestIds, PublishRequestIds>(
        `${PUBLICATION_URL}/candidates/revert-request-dependencies`,
        request,
    );
