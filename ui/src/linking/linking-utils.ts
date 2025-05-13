import { filterUnique, objectEntries } from 'utils/array-utils';
import { JointNumber } from 'common/common-model';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { expectDefined } from 'utils/type-utils';
import { SwitchLinkingTrackLinks } from 'linking/linking-model';

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
    trackLinks: Record<LocationTrackId, SwitchLinkingTrackLinks>,
): LocationTracksEndingAtJoint[] {
    return objectEntries(
        objectEntries(trackLinks)
            .flatMap(([locationTrackId, links]) =>
                (links.suggestedLinks?.joints ?? []).map(
                    (joint) => [joint.jointNumber, locationTrackId] as const,
                ),
            )
            .reduce(
                (acc, pair) => {
                    (acc[pair[0]] ||= []).push(pair[1]);
                    return acc;
                },
                {} as Record<JointNumber, LocationTrackId[]>,
            ),
    )
        .map(([jointNumber, locationTrackIds]) => ({ jointNumber, locationTrackIds }))
        .filter(({ jointNumber }) => jointNumbers.includes(jointNumber));
}

export function combineLocationTrackIds(
    locationTracks: LocationTracksEndingAtJoint[][],
): LocationTracksEndingAtJoint[] {
    if (locationTracks.flat().length === 0) {
        return [];
    }

    return Object.values(
        locationTracks.flat().reduce(
            (acc, locationTrack) => {
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
            },
            {} as { [key: JointNumber]: LocationTracksEndingAtJoint },
        ),
    );
}
