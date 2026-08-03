import * as React from 'react';
import { JointNumber, LayoutContext, SwitchAlignment } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import {
    LayoutSwitchId,
    LayoutSwitchJointConnection,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import {
    combineLocationTrackIds,
    getLocationTracksEndingAtJoints,
    getLocationTracksForJointConnections,
    getMatchingLocationTrackIdsForJointNumbers,
    getSuggestedSwitchAlignmentFit,
} from 'linking/linking-utils';
import { useLoader } from 'utils/react-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { switchJointNumberToString } from 'utils/enum-localization-utils';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import styles from './switch-infobox.scss';
import { SuggestedSwitch, TopologicalJointConnection } from 'linking/linking-model';
import { getLocationTracks } from 'track-layout/layout-location-track-api';
import { useSwitches } from 'track-layout/track-layout-react-utils';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';

type SwitchJointInfobox = {
    switchAlignments: SwitchAlignment[];
    jointConnections: LayoutSwitchJointConnection[];
    topologicalJointConnections?: TopologicalJointConnection[];
    switchesToDetach?: LayoutSwitchId[];
    layoutContext: LayoutContext;
    onSelectLocationTrackBadge?: (locationTrackId: LocationTrackId) => void;
    isLinkingOrSplitting?: boolean;
    // When set, each alignment's location tracks also display the suggestion's fit on the track
    fitSuggestedSwitch?: SuggestedSwitch;
};

const SwitchJointInfobox: React.FC<SwitchJointInfobox> = ({
    switchAlignments,
    jointConnections,
    topologicalJointConnections,
    switchesToDetach,
    layoutContext,
    onSelectLocationTrackBadge,
    isLinkingOrSplitting,
    fitSuggestedSwitch,
}) => {
    const { t } = useTranslation();
    const locationTracksEndingAtJoint = combineLocationTrackIds(
        switchAlignments.map((switchAlignment) =>
            getLocationTracksEndingAtJoints(switchAlignment.jointNumbers, jointConnections),
        ),
    );

    const displayedLocationTracksEndingAtJoint =
        topologicalJointConnections ?? locationTracksEndingAtJoint;

    const locationTracks = [
        useLoader(
            () => getLocationTracksForJointConnections(layoutContext, jointConnections),
            [
                switchAlignments,
                jointConnections,
                layoutContext.branch,
                layoutContext.publicationState,
            ],
        ),
        useLoader(
            () =>
                getLocationTracks(
                    (topologicalJointConnections ?? []).flatMap(
                        (jointConnection) => jointConnection.locationTrackIds,
                    ),
                    layoutContext,
                ),
            [],
        ),
    ]
        .flat()
        .filter(filterNotEmpty);

    const locationTrackBadgeOnClickHandler = (locationTrackId: LocationTrackId) =>
        onSelectLocationTrackBadge ? () => onSelectLocationTrackBadge(locationTrackId) : undefined;

    function getLocationTracksForJointNumbers(jointNumbers: JointNumber[]) {
        const locationTrackIds = getMatchingLocationTrackIdsForJointNumbers(
            jointNumbers,
            jointConnections,
        );
        return getLocationTrackBadges(locationTrackIds, jointNumbers);
    }

    function getLocationTrackBadges(
        locationTrackIds: LocationTrackId[],
        alignmentJointNumbers?: JointNumber[],
    ) {
        const badges = locationTrackIds
            .map((trackId) => locationTracks?.find((locationTrack) => locationTrack.id === trackId))
            .filter(filterNotEmpty)
            .map((track) => {
                const fit =
                    fitSuggestedSwitch && alignmentJointNumbers
                        ? getSuggestedSwitchAlignmentFit(
                              fitSuggestedSwitch,
                              track.id,
                              alignmentJointNumbers,
                          )
                        : undefined;
                return (
                    <div key={track.id} className={styles['switch-joint-infobox__location-track']}>
                        <LocationTrackBadge
                            locationTrack={track}
                            status={
                                isLinkingOrSplitting ? LocationTrackBadgeStatus.DISABLED : undefined
                            }
                            onClick={locationTrackBadgeOnClickHandler(track.id)}
                        />
                        {fit !== undefined && (
                            <span className={styles['switch-joint-infobox__linking-fit']}>
                                {t('tool-panel.switch.layout.linking-fit', {
                                    fit: fit.toFixed(3),
                                })}
                            </span>
                        )}
                    </div>
                );
            });

        return badges.length > 0 ? (
            badges
        ) : (
            <span className={styles['switch-joint-infobox__no-alignments']}>
                {t('tool-panel.switch.layout.no-alignments')}
            </span>
        );
    }

    const switchItemsToDetach = useSwitches(switchesToDetach, layoutContext);

    return (
        <React.Fragment>
            <dl className={styles['switch-joint-infobox__joints-container']}>
                <dt className={styles['switch-joint-infobox__joint-title']}>
                    {t('tool-panel.switch.layout.joint-alignments-title')}
                </dt>
                <dd className={styles['switch-joint-infobox__joint-title']}>
                    {t('tool-panel.switch.layout.joint-alignments-location-tracks-title')}
                </dd>
                {switchAlignments.map((a) => (
                    <React.Fragment key={a.jointNumbers.join('_')}>
                        <dt className={styles['switch-joint-infobox__joint-alignments-title']}>
                            {a.jointNumbers.map((j) => switchJointNumberToString(j)).join('-')}
                        </dt>
                        <dd className={styles['switch-joint-infobox__location-tracks']}>
                            <div>{getLocationTracksForJointNumbers(a.jointNumbers)}</div>
                        </dd>
                    </React.Fragment>
                ))}
                {displayedLocationTracksEndingAtJoint.length > 0 && (
                    <React.Fragment>
                        <dt className={styles['switch-joint-infobox__joint-title']}>
                            {t('tool-panel.switch.layout.joint-number-title')}
                        </dt>
                        <dd className={styles['switch-joint-infobox__joint-title']}>
                            {t('tool-panel.switch.layout.location-tracks-end-at-joint-title')}
                        </dd>
                        {displayedLocationTracksEndingAtJoint?.map((a) => (
                            <React.Fragment key={a.jointNumber}>
                                <dt className={styles['switch-joint-infobox__joint-points-title']}>
                                    {switchJointNumberToString(a.jointNumber)}.{' '}
                                    {t('tool-panel.switch.layout.point')}
                                </dt>
                                <dd className={styles['switch-joint-infobox__location-tracks']}>
                                    <div>{getLocationTrackBadges(a.locationTrackIds)}</div>
                                </dd>
                            </React.Fragment>
                        ))}
                    </React.Fragment>
                )}
            </dl>
            {switchItemsToDetach && switchItemsToDetach.length > 0 && (
                <MessageBox>
                    <dt className={styles['switch-joint-infobox__joint-title']}>
                        {t('tool-panel.switch.layout.switches-to-detach-title', {
                            switchName: switchItemsToDetach.map((s) => s.name).join(', '),
                        })}
                    </dt>
                </MessageBox>
            )}
        </React.Fragment>
    );
};

export default SwitchJointInfobox;
