import { subMonths } from 'date-fns';
import {
    CalculatedChanges,
    DraftChangeType,
    PublicationCandidate,
    PublicationCandidateId,
    PublicationCandidateReference,
    PublicationGroupId,
    PublicationSearch,
    PublicationStage,
} from 'publication/publication-model';
import { currentDay } from 'utils/date-utils';
import { candidateIdAndTypeMatches } from 'preview/preview-view-filters';
import { brand } from 'common/brand';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

export const defaultPublicationSearch: PublicationSearch = {
    startDate: subMonths(currentDay, 1).toISOString(),
    endDate: currentDay.toISOString(),
};

export const conditionallyUpdateCandidates = (
    publicationCandidates: PublicationCandidate[],
    condition: (candidate: PublicationCandidate) => boolean,
    transform: (candidate: PublicationCandidate) => PublicationCandidate,
): PublicationCandidate[] => {
    return publicationCandidates.map((candidate): PublicationCandidate => {
        return condition(candidate) ? transform(candidate) : candidate;
    });
};

export const stageTransform = (
    newStage: PublicationStage,
): ((candidate: PublicationCandidate) => PublicationCandidate) => {
    return (candidate): PublicationCandidate => ({
        ...candidate,
        stage: newStage,
        validationState: 'IN_PROGRESS',
    });
};

export type PublicationAssetChangeAmounts = {
    total: number;
    staged: number;
    unstaged: number;
    groupAmounts: PublicationGroupAmounts;
    ownUnstaged: number;
};

export type PublicationGroupAmounts = Record<PublicationGroupId, number>;

export const countPublicationGroupAmounts = (
    publicationCandidates: PublicationCandidate[],
): Record<PublicationGroupId, number> => {
    return publicationCandidates.reduce((groupSizes, candidate) => {
        const publicationGroupId = candidate.publicationGroup?.id;

        if (publicationGroupId) {
            groupSizes[publicationGroupId] !== undefined
                ? (groupSizes[publicationGroupId] += 1)
                : (groupSizes[publicationGroupId] = 1);
        }

        return groupSizes;
    }, {} as PublicationGroupAmounts);
};

export const createPublicationCandidateReference = (
    id: PublicationCandidateId,
    type: DraftChangeType,
): PublicationCandidateReference => {
    switch (type) {
        case DraftChangeType.TRACK_NUMBER:
            return { id: brand(id), type };

        case DraftChangeType.LOCATION_TRACK:
            return { id: brand(id), type };

        case DraftChangeType.REFERENCE_LINE:
            return { id: brand(id), type };

        case DraftChangeType.SWITCH:
            return { id: brand(id), type };

        case DraftChangeType.KM_POST:
            return { id: brand(id), type };

        default:
            return exhaustiveMatchingGuard(type);
    }
};

export const asPublicationCandidateReferences = (
    publicationCandidates: PublicationCandidate[],
): PublicationCandidateReference[] => {
    return publicationCandidates.map((candidate): PublicationCandidateReference => {
        return createPublicationCandidateReference(candidate.id, candidate.type);
    });
};

export const addValidationState = (
    publicationCandidates: PublicationCandidate[],
    validationGroup: PublicationCandidate[],
): PublicationCandidate[] => {
    return publicationCandidates.map((candidate): PublicationCandidate => {
        const validatedCandidate = validationGroup.find((validatedCandidate) =>
            candidateIdAndTypeMatches(validatedCandidate, candidate),
        );

        return validatedCandidate
            ? {
                  ...candidate,
                  issues: validatedCandidate.issues,
                  validationState: 'API_CALL_OK',
              }
            : candidate;
    });
};

export const setValidationStateToApiError = (
    candidate: PublicationCandidate,
): PublicationCandidate => ({
    ...candidate,
    validationState: 'API_CALL_ERROR',
    issues: [],
});

export const noCalculatedChanges: CalculatedChanges = {
    directChanges: {
        kmPostChanges: [],
        locationTrackChanges: [],
        switchChanges: [],
        referenceLineChanges: [],
        trackNumberChanges: [],
    },
    indirectChanges: {
        locationTrackChanges: [],
        switchChanges: [],
        trackNumberChanges: [],
    },
};
