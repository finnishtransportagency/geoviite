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
    PublishRequest,
    PublishRequestIds,
    PublishResult,
    SplitInPublication,
    PublishCandidate,
    ValidatedPublishCandidates,
    PublishCandidateId,
    DraftChangeType,
    PublicationStage,
    PublishCandidateReference,
    TrackNumberPublishCandidate,
    LocationTrackPublishCandidate,
    ReferenceLinePublishCandidate,
    SwitchPublishCandidate,
    KmPostPublishCandidate,
} from 'publication/publication-model';
import i18next from 'i18next';
import { PublicationDetailsTableSortField } from 'publication/table/publication-table-utils';
import { SortDirection } from 'utils/table-utils';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

const PUBLICATION_URL = `${API_URI}/publications`;

export type PublishCandidatesResponse = {
    trackNumbers: TrackNumberPublishCandidate[];
    locationTracks: LocationTrackPublishCandidate[];
    referenceLines: ReferenceLinePublishCandidate[];
    switches: SwitchPublishCandidate[];
    kmPosts: KmPostPublishCandidate[];
};

export type ValidatedPublishCandidatesResponse = {
    validatedAsPublicationUnit: PublishCandidatesResponse;
    allChangesValidated: PublishCandidatesResponse;
};

export const emptyPublishRequestIds = (): PublishRequestIds => ({
    trackNumbers: [],
    locationTracks: [],
    referenceLines: [],
    switches: [],
    kmPosts: [],
});

const addCandidateTypeAndState = (
    unknownCandidate: PublishCandidate,
    type: DraftChangeType,
): PublishCandidate => {
    return {
        ...unknownCandidate,
        type,
        validated: false,
        pendingValidation: true,
        stage: PublicationStage.UNSTAGED,
    } as PublishCandidate;
};

const createPublishCandidateReference = (
    id: PublishCandidateId,
    type: DraftChangeType,
): PublishCandidateReference => {
    return {
        id,
        type,
    };
};

const toPublishCandidates = (
    publishCandidatesResponse: PublishCandidatesResponse,
): PublishCandidate[] => {
    return [
        publishCandidatesResponse.trackNumbers.map((candidate) =>
            addCandidateTypeAndState(candidate, DraftChangeType.TRACK_NUMBER),
        ),
        publishCandidatesResponse.locationTracks.map((candidate) =>
            addCandidateTypeAndState(candidate, DraftChangeType.LOCATION_TRACK),
        ),
        publishCandidatesResponse.referenceLines.map((candidate) =>
            addCandidateTypeAndState(candidate, DraftChangeType.REFERENCE_LINE),
        ),
        publishCandidatesResponse.switches.map((candidate) =>
            addCandidateTypeAndState(candidate, DraftChangeType.SWITCH),
        ),
        publishCandidatesResponse.kmPosts.map((candidate) =>
            addCandidateTypeAndState(candidate, DraftChangeType.KM_POST),
        ),
    ].flat();
};

const toPublishCandidateReferences = (
    publishRequestIds: PublishRequestIds,
): PublishCandidateReference[] => {
    return [
        publishRequestIds.trackNumbers.map((candidateId) =>
            createPublishCandidateReference(candidateId, DraftChangeType.TRACK_NUMBER),
        ),
        publishRequestIds.locationTracks.map((candidateId) =>
            createPublishCandidateReference(candidateId, DraftChangeType.LOCATION_TRACK),
        ),
        publishRequestIds.referenceLines.map((candidateId) =>
            createPublishCandidateReference(candidateId, DraftChangeType.REFERENCE_LINE),
        ),
        publishRequestIds.switches.map((candidateId) =>
            createPublishCandidateReference(candidateId, DraftChangeType.SWITCH),
        ),
        publishRequestIds.kmPosts.map((candidateId) =>
            createPublishCandidateReference(candidateId, DraftChangeType.KM_POST),
        ),
    ].flat();
};

const toPublishRequestIds = (
    publishCandidates: (PublishCandidate | PublishCandidateReference)[],
): PublishRequestIds => {
    return publishCandidates.reduce((publishRequestIds, candidate) => {
        const candidateType: DraftChangeType = candidate.type;

        switch (candidateType) {
            case DraftChangeType.TRACK_NUMBER:
                publishRequestIds.trackNumbers.push(candidate.id);
                break;
            case DraftChangeType.LOCATION_TRACK:
                publishRequestIds.locationTracks.push(candidate.id);
                break;
            case DraftChangeType.REFERENCE_LINE:
                publishRequestIds.referenceLines.push(candidate.id);
                break;
            case DraftChangeType.SWITCH:
                publishRequestIds.switches.push(candidate.id);
                break;
            case DraftChangeType.KM_POST:
                publishRequestIds.kmPosts.push(candidate.id);
                break;

            default:
                exhaustiveMatchingGuard(candidateType);
        }

        return publishRequestIds;
    }, emptyPublishRequestIds());
};

const toValidatedPublishCandidates = (
    response: ValidatedPublishCandidatesResponse,
): ValidatedPublishCandidates => {
    return {
        validatedAsPublicationUnit: toPublishCandidates(response.validatedAsPublicationUnit),
        allChangesValidated: toPublishCandidates(response.allChangesValidated),
    };
};

export const getPublishCandidates = (): Promise<PublishCandidate[]> =>
    getNonNull<PublishCandidatesResponse>(`${PUBLICATION_URL}/candidates`).then(
        toPublishCandidates,
    );

export const validatePublishCandidates = (candidates: PublishCandidateReference[]) =>
    postNonNull<PublishRequestIds, ValidatedPublishCandidatesResponse>(
        `${PUBLICATION_URL}/validate`,
        toPublishRequestIds(candidates),
    ).then(toValidatedPublishCandidates);

export const revertCandidates = (candidates: PublishCandidateReference[]) =>
    deleteNonNullAdt<PublishRequestIds, PublishResult>(
        `${PUBLICATION_URL}/candidates`,
        toPublishRequestIds(candidates),
    );

export const publishCandidates = (candidates: PublishCandidateReference[], message: string) => {
    const request: PublishRequest = {
        content: toPublishRequestIds(candidates),
        message,
    };

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

export const getCalculatedChanges = (candidates: PublishCandidateReference[]) =>
    postNonNull<PublishRequestIds, CalculatedChanges>(
        `${PUBLICATION_URL}/calculated-changes`,
        toPublishRequestIds(candidates),
    );

export const getRevertRequestDependencies = (candidates: PublishCandidateReference[]) =>
    postNonNull<PublishRequestIds, PublishRequestIds>(
        `${PUBLICATION_URL}/candidates/revert-request-dependencies`,
        toPublishRequestIds(candidates),
    ).then(toPublishCandidateReferences);

export const getSplitDetails = (id: string) =>
    getNonNull<SplitInPublication>(`${PUBLICATION_URL}/${id}/split-details`);

export const splitDetailsCsvUri = (id: string) => `${PUBLICATION_URL}/${id}/split-details/csv`;
