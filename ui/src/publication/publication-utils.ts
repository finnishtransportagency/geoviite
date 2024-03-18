import { subMonths } from 'date-fns';
import {
    PublicationGroupId,
    PublicationSearch,
    PublicationStage,
    PublishCandidate,
} from 'publication/publication-model';
import { currentDay } from 'utils/date-utils';

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
