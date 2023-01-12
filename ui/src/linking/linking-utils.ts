import { filterNotEmpty, filterUnique } from 'utils/array-utils';
import { SuggestedSwitch, SuggestedSwitchJoint } from 'linking/linking-model';
import { JointNumber, PublishType } from 'common/common-model';
import {
    LayoutLocationTrack,
    LayoutSwitchJointConnection,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { getLocationTrack } from 'track-layout/layout-location-track-api';

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
                                otherJoint.number != outerJoint.number &&
                                otherJoint.accurateMatches.some(
                                    (outerMatch) => outerMatch.locationTrackId == locationTrackId,
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
        return jointsOfAlignment.every((joint) =>
            joint.accurateMatches.some((m) => m.locationTrackId == locationTrackId),
        );
    });
}

export function getMatchingLocationTrackIdsForJointNumbers(
    jointNumbers: JointNumber[],
    joints: LayoutSwitchJointConnection[],
): LocationTrackId[] {
    return getMatchingLocationTrackIdsForJoints(
        joints.filter((joint) => jointNumbers.includes(joint.number)),
    );
}

export function getLocationTracksForJointConnections(
    publishType: PublishType,
    joints: LayoutSwitchJointConnection[],
): Promise<LayoutLocationTrack[]> {
    const locationTrackIds = joints
        .flatMap((joint) => joint.accurateMatches.map((m) => m.locationTrackId))
        .filter(filterUnique);

    // This currently flickers when using mass fetch. This can be moved to mass fetch after GVT-1428 is done
    return Promise.all(locationTrackIds.map((id) => getLocationTrack(id, publishType))).then(
        (tracks) => tracks.filter(filterNotEmpty),
    );
}

export function getSuggestedSwitchId(suggestedSwitch: SuggestedSwitch): string {
    return `${suggestedSwitch.geometrySwitchId}_${
        suggestedSwitch.alignmentEndPoint?.locationTrackId
    }_${suggestedSwitch.joints.map(
        (j) => j.number + j.matches.map((m) => m.locationTrackId + m.segmentIndex + m.segmentM),
    )}`;
}

export function asTrackLayoutSwitchJointConnection({
    location,
    number,
    matches,
    locationAccuracy,
}: SuggestedSwitchJoint): LayoutSwitchJointConnection {
    return {
        accurateMatches: matches.map((m) => ({
            locationTrackId: m.locationTrackId,
            location: location,
        })),
        number: number,
        locationAccuracy: locationAccuracy,
    };
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
                    locationTrackIds: acc[jointNumber].locationTrackIds
                        .concat(locationTrack.locationTrackIds)
                        .filter(filterUnique),
                };
            } else {
                acc[jointNumber] = locationTrack;
            }

            return acc;
        }, {} as { [key: JointNumber]: LocationTracksEndingAtJoint }),
    );
}
