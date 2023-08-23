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
    PublicationTableItem,
    PublishCandidates,
    PublishRequest,
    PublishRequestIds,
    PublishResult,
    ValidatedPublishCandidates,
} from 'publication/publication-model';
import {
    PublicationDetailsTableSortField,
    SortDirection,
} from 'publication/table/publication-table-utils';
import i18next from 'i18next';

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

export const getLatestPublications = (count: number) => {
    const params = queryParams({
        count,
    });

    return getIgnoreError<Page<PublicationDetails>>(`${PUBLICATION_URL}/latest${params}`);
};

export const getPublicationAsTableItems = (id: PublicationId) =>
    getIgnoreError<PublicationTableItem[]>(
        `${PUBLICATION_URL}/${id}/table-rows${queryParams({ lang: i18next.language })}`,
    );

export const getPublicationsAsTableItems = (
    from?: Date,
    to?: Date,
    sortBy?: PublicationDetailsTableSortField,
    order?: SortDirection,
) => {
    const isSorted = order != SortDirection.UNSORTED;

    const params = queryParams({
        from: from ? from.toISOString() : undefined,
        to: to ? to.toISOString() : undefined,
        sortBy: isSorted && sortBy ? sortBy : undefined,
        order: isSorted ? order : undefined,
        lang: i18next.language,
    });

    return getIgnoreError<Page<PublicationTableItem>>(`${PUBLICATION_URL}/table-rows${params}`);
};

export const getPublicationsCsvUri = (
    fromDate?: Date,
    toDate?: Date,
    sortBy?: PublicationDetailsTableSortField,
    order?: SortDirection,
): string => {
    const isSorted = order != SortDirection.UNSORTED;

    const params = queryParams({
        from: fromDate ? fromDate.toISOString() : undefined,
        to: toDate ? toDate.toISOString() : undefined,
        sortBy: isSorted && sortBy ? sortBy : undefined,
        order: isSorted ? order : undefined,
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        lang: i18next.language,
    });

    return `${PUBLICATION_URL}/csv${params}`;
};

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
