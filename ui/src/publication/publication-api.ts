import { API_URI, deleteAdt, getIgnoreError, postAdt, postIgnoreError } from 'api/api-fetch';
import { JointNumber, KmNumber, Oid, TrackMeter } from 'common/common-model';
import {
    PublicationDetails,
    PublicationId,
    PublicationListingItem,
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
import { formatDateFull } from 'utils/date-utils';

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

//merge later
export const getPublicationsForFrontpage = () =>
    getIgnoreError<PublicationListingItem[]>(`${PUBLICATION_URL}/frontpage`);

export const getPublications = (
    startDate: Date | undefined,
    endDate: Date | undefined,
): Promise<PublicationDetails | null> => {
    //http://localhost:8080/publications?from=2022-12-29T00:00:00Z&to=2022-12-31T11:50:00Z
    const params = publicationsParams(startDate, endDate);
    return getIgnoreError<PublicationDetails>(`${PUBLICATION_URL}${params}`);
};

export const getPublication = (id: PublicationId) =>
    getIgnoreError<PublicationDetails>(`${PUBLICATION_URL}/${id}`);

export const getCalculatedChanges = (request: PublishRequest) =>
    postIgnoreError<PublishRequest, CalculatedChanges>(
        `${PUBLICATION_URL}/calculated-changes`,
        request,
    );

const publicationsParams = (startDate: Date | undefined, endDate: Date | undefined) => {
    const start = startDate && formatDateFull(startDate);
    const end = endDate && formatDateFull(endDate);

    console.log('start and end', start, end);

    if (startDate && endDate)
        return `?from=${formatDateFull(startDate)}&to=${formatDateFull(endDate)}`;
    else if (startDate && endDate == undefined) return `?from=${formatDateFull(startDate)}`;
    else if (endDate && startDate == undefined) return `?to=${formatDateFull(endDate)}`;
    else return '';
};
