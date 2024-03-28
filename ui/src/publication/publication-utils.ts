import { subMonths } from 'date-fns';
import {
    PublicationGroupId,
    PublicationSearch,
    PublicationStage,
    PublicationCandidate,
    PublicationCandidateReference,
    DraftChangeType,
    PublicationCandidateId,
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
    return (candidate) => ({
        ...candidate,
        stage: newStage,
        pendingValidation: true,
    });
};

export type PublicationAssetChangeAmounts = {
    total: number;
    staged: number;
    unstaged: number;
    groupAmounts: Record<PublicationGroupId, number>;
    ownUnstaged: number;
};

export const countPublicationGroupAmounts = (
    publicationCandidates: PublicationCandidate[],
): Record<PublicationGroupId, number> => {
    return publicationCandidates.reduce((groupSizes, candidate) => {
        const publicationGroupId = candidate.publicationGroup?.id;

        if (publicationGroupId) {
            publicationGroupId in groupSizes
                ? (groupSizes[publicationGroupId] += 1)
                : (groupSizes[publicationGroupId] = 1);
        }

        return groupSizes;
    }, {} as Record<PublicationGroupId, number>);
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
    return publicationCandidates.map((candidate) => {
        const validatedCandidate = validationGroup.find((validatedCandidate) =>
            candidateIdAndTypeMatches(validatedCandidate, candidate),
        );

        return validatedCandidate
            ? {
                  ...candidate,
                  errors: validatedCandidate.errors,
                  pendingValidation: false,
              }
            : candidate;
    });
};
