import { API_URI, deleteAdt, getIgnoreError, postAdt, postIgnoreError } from 'api/api-fetch';
import { JointNumber, KmNumber, Oid, TrackMeter } from 'common/common-model';
import {
    PublicationDetails,
    PublicationId,
    PublicationListingItem,
    PublishCandidates,
} from 'publication/publication-model';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';

const PUBLISH_URI = `${API_URI}/publications`;

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

export interface ValidatedPublishCandidates {
    validatedAsPublicationUnit: PublishCandidates;
    validatedSeparately: PublishCandidates;
}

export const getPublishCandidates = () =>
    getIgnoreError<PublishCandidates>(`${PUBLISH_URI}/candidates`);

export const validatePublishCandidates = (request: PublishRequest) =>
    postIgnoreError<PublishRequest, ValidatedPublishCandidates>(`${PUBLISH_URI}/validate`, request)

export const revertCandidates = () =>
    deleteAdt<null, PublishResult>(`${PUBLISH_URI}/candidates`, null, true);

export const publishCandidates = (request: PublishRequest) => {
    return postAdt<PublishRequest, PublishResult>(`${PUBLISH_URI}`, request, true);
};

export const getPublications = () => getIgnoreError<PublicationListingItem[]>(`${PUBLISH_URI}/`);

export const getPublication = (id: PublicationId) =>
    getIgnoreError<PublicationDetails>(`${PUBLISH_URI}/${id}`);

export const getCalculatedChanges = (request: PublishRequest) =>
    postIgnoreError<PublishRequest, CalculatedChanges>(
        `${PUBLISH_URI}/calculated-changes`,
        request,
    );
