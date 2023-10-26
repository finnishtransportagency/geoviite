import { PublishCandidates, PublishRequestIds } from 'publication/publication-model';
import { filterByIdNotIn, filterIn, filterNotIn } from 'utils/array-utils';

export const addPublishRequestIds = (
    a: PublishRequestIds,
    b: PublishRequestIds,
): PublishRequestIds => ({
    trackNumbers: [...new Set([...a.trackNumbers, ...b.trackNumbers])],
    locationTracks: [...new Set([...a.locationTracks, ...b.locationTracks])],
    referenceLines: [...new Set([...a.referenceLines, ...b.referenceLines])],
    switches: [...new Set([...a.switches, ...b.switches])],
    kmPosts: [...new Set([...a.kmPosts, ...b.kmPosts])],
});

export const subtractPublishRequestIds = (a: PublishRequestIds, b: PublishRequestIds) => ({
    trackNumbers: a.trackNumbers.filter(filterNotIn(b.trackNumbers)),
    locationTracks: a.locationTracks.filter(filterNotIn(b.locationTracks)),
    referenceLines: a.referenceLines.filter(filterNotIn(b.referenceLines)),
    switches: a.switches.filter(filterNotIn(b.switches)),
    kmPosts: a.kmPosts.filter(filterNotIn(b.kmPosts)),
});

export const intersectPublishRequestIds = (a: PublishRequestIds, b: PublishRequestIds) => ({
    trackNumbers: a.trackNumbers.filter(filterIn(b.trackNumbers)),
    locationTracks: a.locationTracks.filter(filterIn(b.locationTracks)),
    referenceLines: a.referenceLines.filter(filterIn(b.referenceLines)),
    switches: a.switches.filter(filterIn(b.switches)),
    kmPosts: a.kmPosts.filter(filterIn(b.kmPosts)),
});

export const dropIdsFromPublishCandidates = (
    publishCandidates: PublishCandidates,
    ids: PublishRequestIds,
): PublishCandidates => ({
    trackNumbers: publishCandidates.trackNumbers.filter(
        filterByIdNotIn(ids.trackNumbers, ({ id }) => id),
    ),
    referenceLines: publishCandidates.referenceLines.filter(
        filterByIdNotIn(ids.referenceLines, ({ id }) => id),
    ),
    switches: publishCandidates.switches.filter(filterByIdNotIn(ids.switches, ({ id }) => id)),
    locationTracks: publishCandidates.locationTracks.filter(
        filterByIdNotIn(ids.locationTracks, ({ id }) => id),
    ),
    kmPosts: publishCandidates.kmPosts.filter(filterByIdNotIn(ids.kmPosts, ({ id }) => id)),
});
