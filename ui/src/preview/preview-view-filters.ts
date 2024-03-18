import {
    PublicationGroup,
    PublicationStage,
    PublishCandidate,
    PublishCandidateReference,
} from 'publication/publication-model';
import { User } from 'user/user-model';

export const candidateIdAndTypeMatches = (
    candidate: PublishCandidateReference,
    otherCandidate: PublishCandidateReference,
) => {
    return candidate.type === otherCandidate.type && candidate.id === otherCandidate.id;
};
export const filterByPublicationGroup = (
    publishCandidates: PublishCandidate[],
    publicationGroup: PublicationGroup,
): PublishCandidate[] => {
    return publishCandidates.filter(
        (candidate) => candidate.publicationGroup?.id == publicationGroup.id,
    );
};

export const filterByUser = (
    publishCandidates: PublishCandidate[],
    user: User,
): PublishCandidate[] => {
    return publishCandidates.filter((candidate) => candidate.userName === user.details.userName);
};

export const filterByPublicationStage = (
    publishCandidates: PublishCandidate[],
    publicationStage: PublicationStage,
): PublishCandidate[] => {
    return publishCandidates.filter((candidate) => candidate.stage === publicationStage);
};

export const pendingValidations = (publishCandidates: PublishCandidate[]) => {
    return publishCandidates.some((candidate) => candidate.pendingValidation);
};
