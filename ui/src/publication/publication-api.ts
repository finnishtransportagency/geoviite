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

export interface PublishRequest {
    trackNumbers: LayoutTrackNumberId[];
    referenceLines: ReferenceLineId[];
    locationTracks: LocationTrackId[];
    switches: LayoutSwitchId[];
    kmPosts: LayoutKmPostId[];
}

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

export const validatePublishCandidates = (request: PublishRequest) =>
    postIgnoreError<PublishRequest, ValidatedPublishCandidates>(
        `${PUBLICATION_URL}/validate`,
        request,
    );

export const revertCandidates = (request: PublishRequest) =>
    deleteAdt<PublishRequest, PublishResult>(`${PUBLICATION_URL}/candidates`, request, true);

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

export const getPublication = (id: PublicationId) =>
    getIgnoreError<PublicationDetails>(`${PUBLICATION_URL}/${id}`);

export const getCalculatedChanges = (request: PublishRequest) =>
    postIgnoreError<PublishRequest, CalculatedChanges>(
        `${PUBLICATION_URL}/calculated-changes`,
        request,
    );

export const getRevertRequestDependencies = (request: PublishRequest) =>
    postIgnoreError<PublishRequest, PublishRequest>(
        `${PUBLICATION_URL}/candidates/revert-request-dependencies`,
        request,
    );
