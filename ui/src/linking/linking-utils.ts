import { filterNotEmpty, filterUnique, first, last, objectEntries } from 'utils/array-utils';
import { SuggestedSwitch, TopologicalJointConnection } from 'linking/linking-model';
import { JointNumber, LayoutContext } from 'common/common-model';
import {
    LayoutLocationTrack,
    LayoutSwitchJointConnection,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { getLocationTracks } from 'track-layout/layout-location-track-api';
import { expectDefined } from 'utils/type-utils';

export enum SwitchTypeMatch {
    Exact,
    Similar,
    Invalid,
}

type LocationTracksEndingAtJoint = {
    jointNumber: JointNumber;
    locationTrackIds: LocationTrackId[];
};

export function getLocationTracksEndingAtJoints(
    jointNumbers: JointNumber[],
    joints: LayoutSwitchJointConnection[],
): LocationTracksEndingAtJoint[] {
    return jointNumbers
        .map((jointNumber) => joints.find((j) => j.number === jointNumber))
        .filter(filterNotEmpty)
        .map((outerJoint, _) => {
            return {
                jointNumber: outerJoint.number,
                locationTrackIds: outerJoint.accurateMatches
                    .map((match) => match.locationTrackId)
                    .filter((locationTrackId) => {
                        // If this alignment id does not exist in any other joint matches,
                        // it is considered as an ending alignment.
                        const existsInOtherJoints = joints.some(
                            (otherJoint) =>
                                otherJoint.number !== outerJoint.number &&
                                otherJoint.accurateMatches.some(
                                    (outerMatch) => outerMatch.locationTrackId === locationTrackId,
                                ),
                        );
                        return !existsInOtherJoints;
                    }),
            };
        })
        .filter((joints) => joints.locationTrackIds.length);
}

export function getMatchingLocationTrackIdsForJoints(
    jointsOfAlignment: LayoutSwitchJointConnection[],
): LocationTrackId[] {
    const locationTrackIds = jointsOfAlignment
        .flatMap((joint) => joint.accurateMatches.map((m) => m.locationTrackId))
        .filter(filterUnique);
    return locationTrackIds.filter((locationTrackId) => {
        return [first(jointsOfAlignment), last(jointsOfAlignment)]
            .filter(filterNotEmpty)
            .every((joint) =>
                joint.accurateMatches.some((m) => m.locationTrackId === locationTrackId),
            );
    });
}

export function getMatchingLocationTrackIdsForJointNumbers(
    jointNumbers: JointNumber[],
    joints: LayoutSwitchJointConnection[],
): LocationTrackId[] {
    return getMatchingLocationTrackIdsForJoints(
        // retain jointNumbers' ordering (based on switch alignment)
        jointNumbers.flatMap((jointNumber) =>
            joints.filter((joint) => joint.number === jointNumber),
        ),
    );
}

export function getLocationTracksForJointConnections(
    layoutContext: LayoutContext,
    joints: LayoutSwitchJointConnection[],
): Promise<LayoutLocationTrack[]> {
    const locationTrackIds = joints
        .flatMap((joint) => joint.accurateMatches.map((m) => m.locationTrackId))
        .filter(filterUnique);

    // This currently flickers when using mass fetch. This can be moved to mass fetch after GVT-1428 is done
    return getLocationTracks(locationTrackIds, layoutContext).then((tracks) =>
        tracks.filter(filterNotEmpty),
    );
}

export function suggestedSwitchJointsAsLayoutSwitchJointConnections(
    ss: SuggestedSwitch,
): LayoutSwitchJointConnection[] {
    const tracks = objectEntries(ss.trackLinks).flatMap(([locationTrackId, links]) =>
        links.segmentJoints.map((sj) => ({
            number: sj.number,
            locationTrackId,
            location: sj.location,
        })),
    );
    return ss.joints.map((joint) => ({
        number: joint.number,
        locationAccuracy: joint.locationAccuracy,
        accurateMatches: tracks
            .filter(({ number }) => number === joint.number)
            .map(({ locationTrackId, location }) => ({
                locationTrackId,
                location,
            })),
    }));
}

export function suggestedSwitchTopoLinksAsTopologicalJointConnections(
    ss: SuggestedSwitch,
): TopologicalJointConnection[] {
    return objectEntries(
        objectEntries(ss.trackLinks)
            .map(([id, link]) => link.topologyJoint && ([link.topologyJoint.number, id] as const))
            .filter(filterNotEmpty)
            .reduce<{ [key in JointNumber]: LocationTrackId[] }>((acc, [jointNumber, id]) => {
                acc[jointNumber] ||= [];
                expectDefined(acc[jointNumber]).push(id);
                return acc;
            }, {}),
    ).map(([jointNumber, locationTrackIds]) => ({ jointNumber, locationTrackIds }));
}

export function combineLocationTrackIds(
    locationTracks: LocationTracksEndingAtJoint[][],
): LocationTracksEndingAtJoint[] {
    if (locationTracks.flat().length === 0) {
        return [];
    }

    return Object.values(
        locationTracks.flat().reduce((acc, locationTrack) => {
            const jointNumber = locationTrack.jointNumber;

            if (acc[jointNumber]) {
                acc[jointNumber] = {
                    jointNumber: jointNumber,
                    locationTrackIds: expectDefined(
                        acc[jointNumber]?.locationTrackIds
                            .concat(locationTrack.locationTrackIds)
                            .filter(filterUnique),
                    ),
                };
            } else {
                acc[jointNumber] = locationTrack;
            }

            return acc;
        }, {} as { [key: JointNumber]: LocationTracksEndingAtJoint }),
    );
}
