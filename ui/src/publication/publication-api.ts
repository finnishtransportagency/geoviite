import {
    API_URI,
    deleteAdt,
    getIgnoreError,
    Page,
    postAdt,
    postIgnoreError,
    queryParams,
} from 'api/api-fetch';
import {
    CalculatedChanges,
    PublicationDetails,
    PublicationId,
    PublishCandidates,
    PublishRequest,
    PublishRequestIds,
    PublishResult,
    ValidatedPublishCandidates,
} from 'publication/publication-model';
import { SortDirection, SortProps } from 'publication/table/publication-table-utils';

const PUBLICATION_URL = `${API_URI}/publications`;

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
        from: fromDate ? fromDate.toISOString() : '',
        to: toDate ? toDate.toISOString() : '',
    });

    return getIgnoreError<Page<PublicationDetails>>(`${PUBLICATION_URL}${params}`);
};

export const getPublicationsCsvUri = (
    fromDate?: Date,
    toDate?: Date,
    sortBy?: SortProps,
    order?: SortDirection,
): string => {
    const isSorted = order != SortDirection.UNSORTED;

    const params = queryParams({
        from: fromDate ? fromDate.toISOString() : undefined,
        to: toDate ? toDate.toISOString() : undefined,
        sortBy: isSorted && sortBy ? sortBy : undefined,
        order: isSorted ? order : undefined,
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    });

    return `${PUBLICATION_URL}/csv${params}`;
};

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
