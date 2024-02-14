import {
    API_URI,
    deleteNonNullAdt,
    getNonNull,
    Page,
    postNonNull,
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
    SplitInPublication,
    ValidatedPublishCandidates,
} from 'publication/publication-model';
import i18next from 'i18next';
import { PublicationDetailsTableSortField } from 'publication/table/publication-table-utils';
import { SortDirection } from 'utils/table-utils';

const PUBLICATION_URL = `${API_URI}/publications`;

export const getPublishCandidates = () =>
    getNonNull<PublishCandidates>(`${PUBLICATION_URL}/candidates`);

export const validatePublishCandidates = (request: PublishRequestIds) =>
    postNonNull<PublishRequestIds, ValidatedPublishCandidates>(
        `${PUBLICATION_URL}/validate`,
        request,
    );

export const revertCandidates = (request: PublishRequestIds) =>
    deleteNonNullAdt<PublishRequestIds, PublishResult>(`${PUBLICATION_URL}/candidates`, request);

export const publishCandidates = (request: PublishRequest) => {
    return postNonNull<PublishRequest, PublishResult>(`${PUBLICATION_URL}`, request);
};

export const getLatestPublications = (count: number) => {
    const params = queryParams({
        count,
    });

    return getNonNull<Page<PublicationDetails>>(`${PUBLICATION_URL}/latest${params}`).then(
        (page) => page.items,
    );
};

export const getPublication = (id: PublicationId) =>
    getNonNull<PublicationDetails>(`${PUBLICATION_URL}/${id}`);

export const getPublicationAsTableItems = (id: PublicationId) =>
    getNonNull<PublicationTableItem[]>(
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

    return getNonNull<Page<PublicationTableItem>>(`${PUBLICATION_URL}/table-rows${params}`);
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
    postNonNull<PublishRequestIds, CalculatedChanges>(
        `${PUBLICATION_URL}/calculated-changes`,
        request,
    );

export const getRevertRequestDependencies = (request: PublishRequestIds) =>
    postNonNull<PublishRequestIds, PublishRequestIds>(
        `${PUBLICATION_URL}/candidates/revert-request-dependencies`,
        request,
    );

export const getSplitDetails = (id: string) =>
    getNonNull<SplitInPublication>(`${PUBLICATION_URL}/${id}/split-details`);

export const splitDetailsCsvUri = (id: string) => `${PUBLICATION_URL}/${id}/split-details/csv`;
