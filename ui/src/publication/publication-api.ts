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
    DraftChangeType,
    KmPostPublicationCandidate,
    LocationTrackPublicationCandidate,
    PublicationCandidate,
    PublicationCandidateReference,
    PublicationDetails,
    PublicationId,
    PublicationRequest,
    PublicationRequestIds,
    PublicationResult,
    PublicationStage,
    PublicationTableItem,
    ReferenceLinePublicationCandidate,
    SplitInPublication,
    SwitchPublicationCandidate,
    TrackNumberPublicationCandidate,
    ValidatedPublicationCandidates,
} from 'publication/publication-model';
import i18next from 'i18next';
import { PublicationDetailsTableSortField } from 'publication/table/publication-table-utils';
import { SortDirection } from 'utils/table-utils';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { createPublicationCandidateReference } from 'publication/publication-utils';

const PUBLICATION_URL = `${API_URI}/publications`;

export type PublicationCandidatesResponse = {
    trackNumbers: TrackNumberPublicationCandidate[];
    locationTracks: LocationTrackPublicationCandidate[];
    referenceLines: ReferenceLinePublicationCandidate[];
    switches: SwitchPublicationCandidate[];
    kmPosts: KmPostPublicationCandidate[];
};

export type ValidatedPublicationCandidatesResponse = {
    validatedAsPublicationUnit: PublicationCandidatesResponse;
    allChangesValidated: PublicationCandidatesResponse;
};

export const emptyPublicationRequestIds = (): PublicationRequestIds => ({
    trackNumbers: [],
    locationTracks: [],
    referenceLines: [],
    switches: [],
    kmPosts: [],
});

const addCandidateTypeAndState = (
    unknownCandidate: PublicationCandidate,
    type: DraftChangeType,
): PublicationCandidate => {
    return {
        ...unknownCandidate,
        type,
        validated: false,
        pendingValidation: true,
        stage: PublicationStage.UNSTAGED,
    } as PublicationCandidate;
};

const toPublicationCandidates = (
    publicationCandidatesResponse: PublicationCandidatesResponse,
): PublicationCandidate[] => {
    return [
        publicationCandidatesResponse.trackNumbers.map((candidate) =>
            addCandidateTypeAndState(candidate, DraftChangeType.TRACK_NUMBER),
        ),
        publicationCandidatesResponse.locationTracks.map((candidate) =>
            addCandidateTypeAndState(candidate, DraftChangeType.LOCATION_TRACK),
        ),
        publicationCandidatesResponse.referenceLines.map((candidate) =>
            addCandidateTypeAndState(candidate, DraftChangeType.REFERENCE_LINE),
        ),
        publicationCandidatesResponse.switches.map((candidate) =>
            addCandidateTypeAndState(candidate, DraftChangeType.SWITCH),
        ),
        publicationCandidatesResponse.kmPosts.map((candidate) =>
            addCandidateTypeAndState(candidate, DraftChangeType.KM_POST),
        ),
    ].flat();
};

const toPublicationCandidateReferences = (
    publicationRequestIds: PublicationRequestIds,
): PublicationCandidateReference[] => {
    return [
        publicationRequestIds.trackNumbers.map((candidateId) =>
            createPublicationCandidateReference(candidateId, DraftChangeType.TRACK_NUMBER),
        ),
        publicationRequestIds.locationTracks.map((candidateId) =>
            createPublicationCandidateReference(candidateId, DraftChangeType.LOCATION_TRACK),
        ),
        publicationRequestIds.referenceLines.map((candidateId) =>
            createPublicationCandidateReference(candidateId, DraftChangeType.REFERENCE_LINE),
        ),
        publicationRequestIds.switches.map((candidateId) =>
            createPublicationCandidateReference(candidateId, DraftChangeType.SWITCH),
        ),
        publicationRequestIds.kmPosts.map((candidateId) =>
            createPublicationCandidateReference(candidateId, DraftChangeType.KM_POST),
        ),
    ].flat();
};

const toPublicationRequestIds = (
    publicationCandidates: (PublicationCandidate | PublicationCandidateReference)[],
): PublicationRequestIds => {
    return publicationCandidates.reduce((publicationRequestIds, candidate) => {
        switch (candidate.type) {
            case DraftChangeType.TRACK_NUMBER:
                publicationRequestIds.trackNumbers.push(candidate.id);
                break;

            case DraftChangeType.LOCATION_TRACK:
                publicationRequestIds.locationTracks.push(candidate.id);
                break;

            case DraftChangeType.REFERENCE_LINE:
                publicationRequestIds.referenceLines.push(candidate.id);
                break;

            case DraftChangeType.SWITCH:
                publicationRequestIds.switches.push(candidate.id);
                break;

            case DraftChangeType.KM_POST:
                publicationRequestIds.kmPosts.push(candidate.id);
                break;

            default:
                exhaustiveMatchingGuard(candidate);
        }

        return publicationRequestIds;
    }, emptyPublicationRequestIds());
};

const toValidatedPublicationCandidates = (
    response: ValidatedPublicationCandidatesResponse,
): ValidatedPublicationCandidates => {
    return {
        validatedAsPublicationUnit: toPublicationCandidates(response.validatedAsPublicationUnit),
        allChangesValidated: toPublicationCandidates(response.allChangesValidated),
    };
};

export const getPublicationCandidates = (): Promise<PublicationCandidate[]> =>
    getNonNull<PublicationCandidatesResponse>(`${PUBLICATION_URL}/candidates`).then(
        toPublicationCandidates,
    );

export const validatePublicationCandidates = (candidates: PublicationCandidateReference[]) =>
    postNonNull<PublicationRequestIds, ValidatedPublicationCandidatesResponse>(
        `${PUBLICATION_URL}/validate`,
        toPublicationRequestIds(candidates),
    ).then(toValidatedPublicationCandidates);

export const revertPublicationCandidates = (candidates: PublicationCandidateReference[]) =>
    deleteNonNullAdt<PublicationRequestIds, PublicationResult>(
        `${PUBLICATION_URL}/candidates`,
        toPublicationRequestIds(candidates),
    );

export const publishPublicationCandidates = (
    candidates: PublicationCandidateReference[],
    message: string,
) => {
    const request: PublicationRequest = {
        content: toPublicationRequestIds(candidates),
        message,
    };

    return postNonNull<PublicationRequest, PublicationResult>(`${PUBLICATION_URL}`, request);
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

export const getCalculatedChanges = (candidates: PublicationCandidateReference[]) =>
    postNonNull<PublicationRequestIds, CalculatedChanges>(
        `${PUBLICATION_URL}/calculated-changes`,
        toPublicationRequestIds(candidates),
    );

export const getRevertRequestDependencies = (candidates: PublicationCandidateReference[]) =>
    postNonNull<PublicationRequestIds, PublicationRequestIds>(
        `${PUBLICATION_URL}/candidates/revert-request-dependencies`,
        toPublicationRequestIds(candidates),
    ).then(toPublicationCandidateReferences);

export const getSplitDetails = (id: string) =>
    getNonNull<SplitInPublication>(`${PUBLICATION_URL}/${id}/split-details`);

export const splitDetailsCsvUri = (id: string) =>
    `${PUBLICATION_URL}/${id}/split-details/csv${queryParams({ lang: i18next.language })}`;
