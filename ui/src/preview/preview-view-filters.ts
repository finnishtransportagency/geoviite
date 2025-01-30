import {
    PublicationGroup,
    PublicationStage,
    PublicationCandidate,
    PublicationCandidateReference,
} from 'publication/publication-model';
import { User } from 'user/user-model';

export const candidateIdAndTypeMatches = (
    candidate: PublicationCandidateReference,
    otherCandidate: PublicationCandidateReference,
) => {
    return candidate.type === otherCandidate.type && candidate.id === otherCandidate.id;
};
export const filterByPublicationGroup = (
    publicationCandidates: PublicationCandidate[],
    publicationGroup: PublicationGroup,
): PublicationCandidate[] => {
    return publicationCandidates.filter(
        (candidate) => candidate.publicationGroup?.id === publicationGroup.id,
    );
};

export const filterByUser = (
    publicationCandidates: PublicationCandidate[],
    user: User,
): PublicationCandidate[] => {
    return publicationCandidates.filter(
        (candidate) => candidate.userName === user.details.userName,
    );
};

export const filterByPublicationStage = (
    publicationCandidates: PublicationCandidate[],
    publicationStage: PublicationStage,
): PublicationCandidate[] => {
    return publicationCandidates.filter((candidate) => candidate.stage === publicationStage);
};
