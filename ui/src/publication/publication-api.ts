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
    LayoutBranchType,
    LocationTrackPublicationCandidate,
    OperationalPointPublicationCandidate,
    PublicationCandidate,
    PublicationCandidateReference,
    PublicationDetails,
    PublicationId,
    PublicationRequest,
    PublicationRequestIds,
    PublicationResultSummary,
    PublicationStage,
    PublicationTableItem,
    ReferenceLinePublicationCandidate,
    SplitInPublication,
    SwitchPublicationCandidate,
    TrackNumberPublicationCandidate,
    ValidatedPublicationCandidates,
} from 'publication/publication-model';
import i18next from 'i18next';
import { SortDirection } from 'utils/table-utils';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { createPublicationCandidateReference } from 'publication/publication-utils';
import { DesignBranch, LayoutBranch, PublicationState } from 'common/common-model';
import {
    LayoutKmPost,
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutReferenceLine,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { SortablePublicationTableProps } from 'publication/table/publication-table-utils';

const PUBLICATION_URL = `${API_URI}/publications`;

function publicationUri(layoutBranch: LayoutBranch): string {
    return `${PUBLICATION_URL}/${layoutBranch.toLowerCase()}`;
}

function mergeToMainUri(designBranch: DesignBranch): string {
    return `${PUBLICATION_URL}/merge-to-main/${designBranch.toLowerCase()}`;
}

export type PublicationCandidatesResponse = {
    trackNumbers: TrackNumberPublicationCandidate[];
    locationTracks: LocationTrackPublicationCandidate[];
    referenceLines: ReferenceLinePublicationCandidate[];
    switches: SwitchPublicationCandidate[];
    kmPosts: KmPostPublicationCandidate[];
    operationalPoints: OperationalPointPublicationCandidate[];
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
    operationalPoints: [],
});

const addCandidateTypeAndState = (
    unknownCandidate: PublicationCandidate,
    type: DraftChangeType,
): PublicationCandidate => {
    return {
        ...unknownCandidate,
        type,
        validationState: 'IN_PROGRESS',
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
        publicationCandidatesResponse.operationalPoints.map((candidate) =>
            addCandidateTypeAndState(candidate, DraftChangeType.OPERATIONAL_POINT),
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
        publicationRequestIds.operationalPoints.map((candidateId) =>
            createPublicationCandidateReference(candidateId, DraftChangeType.OPERATIONAL_POINT),
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

            case DraftChangeType.OPERATIONAL_POINT:
                publicationRequestIds.operationalPoints.push(candidate.id);
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

export const getPublicationCandidates = (
    layoutBranch: LayoutBranch,
    fromState: PublicationState,
): Promise<PublicationCandidate[]> =>
    getNonNull<PublicationCandidatesResponse>(
        `${publicationUri(layoutBranch)}/${fromState}/candidates`,
    ).then(toPublicationCandidates);

export const validateMergeToMainCandidates = (
    layoutBranch: LayoutBranch,
    candidates: PublicationCandidateReference[],
) =>
    postNonNull<PublicationRequestIds, ValidatedPublicationCandidatesResponse>(
        `${publicationUri(layoutBranch)}/validate-merge-to-main`,
        toPublicationRequestIds(candidates),
    ).then(toValidatedPublicationCandidates);

export const validatePublicationCandidates = (
    layoutBranch: LayoutBranch,
    candidates: PublicationCandidateReference[],
) => validatePublicationCandidatesWith('validate', layoutBranch, candidates);

export const validateMergeToMain = (
    layoutBranch: LayoutBranch,
    candidates: PublicationCandidateReference[],
) => validatePublicationCandidatesWith('validate-merge-to-main', layoutBranch, candidates);

const validatePublicationCandidatesWith = (
    validationPath: string,
    layoutBranch: LayoutBranch,
    candidates: PublicationCandidateReference[],
) =>
    postNonNull<PublicationRequestIds, ValidatedPublicationCandidatesResponse>(
        `${publicationUri(layoutBranch)}/${validationPath}`,
        toPublicationRequestIds(candidates),
    ).then(toValidatedPublicationCandidates);

export const revertPublicationCandidates = (
    layoutBranch: LayoutBranch,
    candidates: PublicationCandidateReference[],
) =>
    deleteNonNullAdt<PublicationRequestIds, PublicationResultSummary>(
        `${publicationUri(layoutBranch)}/candidates`,
        toPublicationRequestIds(candidates),
    );

export const publishPublicationCandidates = (
    layoutBranch: LayoutBranch,
    candidates: PublicationCandidateReference[],
    message: string,
) => {
    const request: PublicationRequest = {
        content: toPublicationRequestIds(candidates),
        message,
    };

    return postNonNull<PublicationRequest, PublicationResultSummary>(
        `${publicationUri(layoutBranch)}`,
        request,
    );
};

export const mergeCandidatesToMain = (
    layoutBranch: DesignBranch,
    candidates: PublicationCandidateReference[],
) => {
    const request = toPublicationRequestIds(candidates);

    return postNonNull<PublicationRequestIds, PublicationResultSummary>(
        `${mergeToMainUri(layoutBranch)}`,
        request,
    );
};

export const getLatestPublications = async (count: number, branchType: LayoutBranchType) => {
    const params = queryParams({
        count,
    });

    return await getNonNull<Page<PublicationDetails>>(
        `${PUBLICATION_URL}/latest/${branchType}${params}`,
    );
};

export const getPublication = (id: PublicationId) =>
    getNonNull<PublicationDetails>(`${PUBLICATION_URL}/${id}`);

export const getPublicationAsTableItems = (id: PublicationId) =>
    getNonNull<PublicationTableItem[]>(
        `${PUBLICATION_URL}/${id}/table-rows${queryParams({ lang: i18next.language })}`,
    );

const PUBLICATION_TABLE_SORT_COLUMN_NAMES: {
    [Tkey in keyof SortablePublicationTableProps]: string;
} = {
    name: 'NAME',
    trackNumbers: 'TRACK_NUMBERS',
    changedKmNumbers: 'CHANGED_KM_NUMBERS',
    operation: 'OPERATION',
    publicationTime: 'PUBLICATION_TIME',
    publicationUser: 'PUBLICATION_USER',
    message: 'MESSAGE',
    ratkoPushTime: 'RATKO_PUSH_TIME',
} as const;

export const MAX_RETURNED_PUBLICATION_LOG_ROWS = 500;
export const getPublicationsAsTableItems = (
    from?: Date,
    to?: Date,
    specificItem?: PublishableObjectIdAndType,
    sortBy?: keyof SortablePublicationTableProps,
    order?: SortDirection,
) => {
    const isSorted = order !== SortDirection.UNSORTED;

    const params = queryParams({
        from: from ? from.toISOString() : undefined,
        to: to ? to.toISOString() : undefined,
        type: specificItem ? specificItem.type : undefined,
        id: specificItem ? specificItem.id : undefined,
        sortBy: isSorted && sortBy ? PUBLICATION_TABLE_SORT_COLUMN_NAMES[sortBy] : undefined,
        order: isSorted ? order : undefined,
        lang: i18next.language,
    });

    return getNonNull<Page<PublicationTableItem>>(`${PUBLICATION_URL}/table-rows${params}`);
};

export type PublishableObjectIdAndType =
    | TrackNumberIdAndType
    | ReferenceLineIdAndType
    | LocationTrackIdAndType
    | SwitchIdAndType
    | KmPostIdAndType;

export type TrackNumberIdAndType = { id: LayoutTrackNumberId; type: 'TRACK_NUMBER' };
export type ReferenceLineIdAndType = { id: ReferenceLineId; type: 'REFERENCE_LINE' };
export type LocationTrackIdAndType = { id: LocationTrackId; type: 'LOCATION_TRACK' };
export type SwitchIdAndType = { id: LayoutSwitchId; type: 'SWITCH' };
export type KmPostIdAndType = { id: LayoutKmPostId; type: 'KM_POST' };

export type PublishedAsset =
    | PublishedAssetTrackNumber
    | PublishedAssetReferenceLine
    | PublishedAssetLocationTrack
    | PublishedAssetSwitch
    | PublishedAssetKmPost;

export type PublishedAssetTrackNumber = { asset: LayoutTrackNumber; type: 'TRACK_NUMBER' };
export type PublishedAssetReferenceLine = { asset: LayoutReferenceLine; type: 'REFERENCE_LINE' };
export type PublishedAssetLocationTrack = { asset: LayoutLocationTrack; type: 'LOCATION_TRACK' };
export type PublishedAssetSwitch = { asset: LayoutSwitch; type: 'SWITCH' };
export type PublishedAssetKmPost = { asset: LayoutKmPost; type: 'KM_POST' };

export const getPublicationsCsvUri = (
    fromDate?: Date,
    toDate?: Date,
    specificObject?: { idAndType: PublishableObjectIdAndType; name: string },
    sortBy?: keyof SortablePublicationTableProps,
    order?: SortDirection,
): string => {
    const isSorted = order !== SortDirection.UNSORTED;

    const params = queryParams({
        from: fromDate ? fromDate.toISOString() : undefined,
        to: toDate ? toDate.toISOString() : undefined,
        id: specificObject?.idAndType?.id,
        type: specificObject?.idAndType?.type,
        filenameObjectPart: specificObject?.name,
        sortBy: isSorted && sortBy ? PUBLICATION_TABLE_SORT_COLUMN_NAMES[sortBy] : undefined,
        order: isSorted ? order : undefined,
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        lang: i18next.language,
    });

    return `${PUBLICATION_URL}/csv${params}`;
};

export const getCalculatedChanges = (
    layoutBranch: LayoutBranch,
    candidates: PublicationCandidateReference[],
) =>
    postNonNull<PublicationRequestIds, CalculatedChanges>(
        `${publicationUri(layoutBranch)}/calculated-changes`,
        toPublicationRequestIds(candidates),
    );

export const getRevertRequestDependencies = (
    layoutBranch: LayoutBranch,
    candidates: PublicationCandidateReference[],
) =>
    postNonNull<PublicationRequestIds, PublicationRequestIds>(
        `${publicationUri(layoutBranch)}/candidates/revert-request-dependencies`,
        toPublicationRequestIds(candidates),
    ).then(toPublicationCandidateReferences);

export const getSplitDetails = (id: string) =>
    getNonNull<SplitInPublication>(`${PUBLICATION_URL}/${id}/split-details`);

export const splitDetailsCsvUri = (id: string) =>
    `${PUBLICATION_URL}/${id}/split-details/csv${queryParams({ lang: i18next.language })}`;
