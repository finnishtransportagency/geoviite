import {
    KmPostPublishCandidate,
    LocationTrackPublishCandidate,
    PublicationGroupId,
    PublishCandidate,
    PublishCandidates,
    PublishRequestIds,
    ReferenceLinePublishCandidate,
    SwitchPublishCandidate,
    TrackNumberPublishCandidate,
} from 'publication/publication-model';
import { AssetId } from 'common/common-model';

export type Candidate = {
    id: AssetId;
};

export type PreviewCandidate = PublishCandidate & PendingValidation;

export type PreviewCandidates = {
    trackNumbers: (TrackNumberPublishCandidate & PendingValidation)[];
    referenceLines: (ReferenceLinePublishCandidate & PendingValidation)[];
    locationTracks: (LocationTrackPublishCandidate & PendingValidation)[];
    switches: (SwitchPublishCandidate & PendingValidation)[];
    kmPosts: (KmPostPublishCandidate & PendingValidation)[];
};

export type PendingValidation = {
    pendingValidation: boolean;
};

export const emptyChanges = {
    trackNumbers: [],
    locationTracks: [],
    referenceLines: [],
    switches: [],
    kmPosts: [],
} satisfies PreviewCandidates | PublishRequestIds;

export const nonPendingCandidate = <T extends PublishCandidate>(candidate: T) => ({
    ...candidate,
    pendingValidation: false,
});
export const pendingCandidate = <T extends PublishCandidate>(candidate: T) => ({
    ...candidate,
    pendingValidation: true,
});

// Validating the change set takes time. After a change is staged, it should be regarded as staged, but pending
// validation until validation is complete
export const pendingValidation = (allStaged: AssetId[], allValidated: AssetId[], id: AssetId) =>
    allStaged.includes(id) && !allValidated.includes(id);

export type PublicationAssetChangeAmounts = {
    total: number;
    staged: number;
    unstaged: number;
    groupAmounts: Record<PublicationGroupId, number>;
    ownUnstaged: number;
};

export const countPublishCandidates = (
    publishCandidates: PublishCandidates | undefined,
): number => {
    if (!publishCandidates) {
        return 0;
    }

    return Object.values(publishCandidates)
        .filter((maybeAssetArray) => Array.isArray(maybeAssetArray))
        .reduce((amount, assetArray) => amount + assetArray.length, 0);
};

export const countPublicationGroupAmounts = (
    changeSet: PublishCandidates | undefined,
): Record<PublicationGroupId, number> => {
    if (!changeSet) {
        return {};
    }

    return Object.values(changeSet)
        .filter((maybeAssetArray) => Array.isArray(maybeAssetArray))
        .flatMap((assetArray) => {
            return assetArray.map((asset) => asset.publicationGroup?.id);
        })
        .filter(
            (publicationGroupId): publicationGroupId is PublicationGroupId => !!publicationGroupId,
        )
        .reduce((groupSizes, publicationGroup) => {
            publicationGroup in groupSizes
                ? (groupSizes[publicationGroup] += 1)
                : (groupSizes[publicationGroup] = 1);

            return groupSizes;
        }, {} as Record<PublicationGroupId, number>);
};
