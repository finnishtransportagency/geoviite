import { subMonths } from 'date-fns';
import {
    PublicationGroupId,
    PublicationSearch,
    PublicationStage,
    PublishCandidate,
    PublishCandidateReference,
} from 'publication/publication-model';
import { currentDay } from 'utils/date-utils';
import { candidateIdAndTypeMatches } from 'preview/preview-view-filters';

export const defaultPublicationSearch: PublicationSearch = {
    startDate: subMonths(currentDay, 1).toISOString(),
    endDate: currentDay.toISOString(),
};

export const conditionallyUpdateCandidates = (
    publishCandidates: PublishCandidate[],
    condition: (candidate: PublishCandidate) => boolean,
    transform: (candidate: PublishCandidate) => PublishCandidate,
): PublishCandidate[] => {
    return publishCandidates.map((candidate): PublishCandidate => {
        if (condition(candidate)) {
            return transform(candidate);
        }

        return candidate;
    });
};

export const stageTransform = (
    newStage: PublicationStage,
): ((candidate: PublishCandidate) => PublishCandidate) => {
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
    publishCandidates: PublishCandidate[],
): Record<PublicationGroupId, number> => {
    return publishCandidates.reduce((groupSizes, candidate) => {
        const publicationGroupId = candidate.publicationGroup?.id;

        if (publicationGroupId) {
            publicationGroupId in groupSizes
                ? (groupSizes[publicationGroupId] += 1)
                : (groupSizes[publicationGroupId] = 1);
        }

        return groupSizes;
    }, {} as Record<PublicationGroupId, number>);
};

export const asPublishCandidateReferences = (
    publishCandidates: PublishCandidate[],
): PublishCandidateReference[] => {
    return publishCandidates.map((candidate) => ({
        id: candidate.id,
        type: candidate.type,
    }));
};

export const addValidationState = (
    publishCandidates: PublishCandidate[],
    validationGroup: PublishCandidate[],
): PublishCandidate[] => {
    return publishCandidates.map((candidate) => {
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
