import {
    PublicationGroup,
    PublishCandidate,
    PublishCandidateId,
    PublishCandidates,
    PublishRequestIds,
    WithId,
} from 'publication/publication-model';
import { AssetId } from 'common/common-model';
import { User } from 'user/user-model';
import {
    Candidate,
    nonPendingCandidate,
    pendingCandidate,
    pendingValidation,
    PreviewCandidate,
    PreviewCandidates,
} from 'preview/preview-view-data';

export const publishCandidateIds = (candidates: PublishCandidates): PublishRequestIds => ({
    trackNumbers: candidates.trackNumbers.map((tn) => tn.id),
    locationTracks: candidates.locationTracks.map((lt) => lt.id),
    referenceLines: candidates.referenceLines.map((rl) => rl.id),
    switches: candidates.switches.map((s) => s.id),
    kmPosts: candidates.kmPosts.map((s) => s.id),
});

const filterStaged = (stagedIds: AssetId[], candidate: Candidate) =>
    stagedIds.includes(candidate.id);
const filterUnstaged = (stagedIds: AssetId[], candidate: Candidate) =>
    !stagedIds.includes(candidate.id);

const filterPreviewCandidateArrayByUser = <T extends PreviewCandidate>(
    user: User,
    candidates: T[],
) => candidates.filter((candidate) => candidate.userName === user.details.userName);

export const getStagedChanges = (
    publishCandidates: PublishCandidates,
    stagedChangeIds: PublishRequestIds,
): PublishCandidates => ({
    trackNumbers: publishCandidates.trackNumbers.filter((trackNumber) =>
        filterStaged(stagedChangeIds.trackNumbers, trackNumber),
    ),
    locationTracks: publishCandidates.locationTracks.filter((locationTrack) =>
        filterStaged(stagedChangeIds.locationTracks, locationTrack),
    ),
    switches: publishCandidates.switches.filter((layoutSwitch) =>
        filterStaged(stagedChangeIds.switches, layoutSwitch),
    ),
    kmPosts: publishCandidates.kmPosts.filter((kmPost) =>
        filterStaged(stagedChangeIds.kmPosts, kmPost),
    ),
    referenceLines: publishCandidates.referenceLines.filter((referenceLine) =>
        filterStaged(stagedChangeIds.referenceLines, referenceLine),
    ),
});

export const getUnstagedChanges = (
    publishCandidates: PublishCandidates,
    stagedChangeIds: PublishRequestIds,
): PublishCandidates => ({
    trackNumbers: publishCandidates.trackNumbers.filter((trackNumber) =>
        filterUnstaged(stagedChangeIds.trackNumbers, trackNumber),
    ),
    locationTracks: publishCandidates.locationTracks.filter((locationTrack) =>
        filterUnstaged(stagedChangeIds.locationTracks, locationTrack),
    ),
    switches: publishCandidates.switches.filter((layoutSwitch) =>
        filterUnstaged(stagedChangeIds.switches, layoutSwitch),
    ),
    kmPosts: publishCandidates.kmPosts.filter((kmPost) =>
        filterUnstaged(stagedChangeIds.kmPosts, kmPost),
    ),
    referenceLines: publishCandidates.referenceLines.filter((referenceLine) =>
        filterUnstaged(stagedChangeIds.referenceLines, referenceLine),
    ),
});

export const previewChanges = (
    stagedValidatedChanges: PublishCandidates,
    allSelectedChanges: PublishRequestIds,
    entireChangeset: PublishCandidates,
): PreviewCandidates => {
    const validatedIds = publishCandidateIds(stagedValidatedChanges);

    return {
        trackNumbers: [
            ...stagedValidatedChanges.trackNumbers.map(nonPendingCandidate),
            ...entireChangeset.trackNumbers
                .filter((change) =>
                    pendingValidation(
                        allSelectedChanges.trackNumbers,
                        validatedIds.trackNumbers,
                        change.id,
                    ),
                )
                .map(pendingCandidate),
        ],
        referenceLines: [
            ...stagedValidatedChanges.referenceLines.map(nonPendingCandidate),
            ...entireChangeset.referenceLines
                .filter((change) =>
                    pendingValidation(
                        allSelectedChanges.referenceLines,
                        validatedIds.referenceLines,
                        change.id,
                    ),
                )
                .map(pendingCandidate),
        ],
        locationTracks: [
            ...stagedValidatedChanges.locationTracks.map(nonPendingCandidate),
            ...entireChangeset.locationTracks
                .filter((change) =>
                    pendingValidation(
                        allSelectedChanges.locationTracks,
                        validatedIds.locationTracks,
                        change.id,
                    ),
                )
                .map(pendingCandidate),
        ],
        switches: [
            ...stagedValidatedChanges.switches.map(nonPendingCandidate),
            ...entireChangeset.switches
                .filter((change) =>
                    pendingValidation(
                        allSelectedChanges.switches,
                        validatedIds.switches,
                        change.id,
                    ),
                )
                .map(pendingCandidate),
        ],
        kmPosts: [
            ...stagedValidatedChanges.kmPosts.map(nonPendingCandidate),
            ...entireChangeset.kmPosts
                .filter((change) =>
                    pendingValidation(allSelectedChanges.kmPosts, validatedIds.kmPosts, change.id),
                )
                .map(pendingCandidate),
        ],
    };
};

export const previewCandidatesByUser = (
    user: User,
    previewCandidates: PreviewCandidates,
): PreviewCandidates => ({
    trackNumbers: filterPreviewCandidateArrayByUser(user, previewCandidates.trackNumbers),
    referenceLines: filterPreviewCandidateArrayByUser(user, previewCandidates.referenceLines),
    locationTracks: filterPreviewCandidateArrayByUser(user, previewCandidates.locationTracks),
    switches: filterPreviewCandidateArrayByUser(user, previewCandidates.switches),
    kmPosts: filterPreviewCandidateArrayByUser(user, previewCandidates.kmPosts),
});

export const filterByPublicationGroup = <Id extends PublishCandidateId>(
    candidates: (PublishCandidate & WithId<Id>)[],
    publicationGroup: PublicationGroup,
) => candidates.filter((candidate) => candidate.publicationGroup?.id === publicationGroup.id);

export const assetIdsByPublicationGroup = <Id extends PublishCandidateId>(
    candidates: (PublishCandidate & WithId<Id>)[],
    publicationGroup: PublicationGroup,
) => {
    return filterByPublicationGroup(candidates, publicationGroup).map((candidate) => candidate.id);
};

export const idsByPublicationGroup = (
    candidates: PublishCandidates,
    publicationGroup: PublicationGroup,
): PublishRequestIds => ({
    trackNumbers: assetIdsByPublicationGroup(candidates.trackNumbers, publicationGroup),
    referenceLines: assetIdsByPublicationGroup(candidates.referenceLines, publicationGroup),
    locationTracks: assetIdsByPublicationGroup(candidates.locationTracks, publicationGroup),
    switches: assetIdsByPublicationGroup(candidates.switches, publicationGroup),
    kmPosts: assetIdsByPublicationGroup(candidates.kmPosts, publicationGroup),
});
