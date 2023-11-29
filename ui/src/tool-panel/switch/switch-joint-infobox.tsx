import * as React from 'react';
import { JointNumber, PublishType, SwitchAlignment } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import { LayoutSwitchJointConnection, LocationTrackId } from 'track-layout/track-layout-model';
import {
    combineLocationTrackIds,
    getLocationTracksEndingAtJoints,
    getLocationTracksForJointConnections,
    getMatchingLocationTrackIdsForJointNumbers,
} from 'linking/linking-utils';
import { useLoader } from 'utils/react-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { switchJointNumberToString } from 'utils/enum-localization-utils';
import { LocationTrackBadge } from 'geoviite-design-lib/alignment/location-track-badge';
import styles from './switch-infobox.scss';
import { TopologicalJointConnection } from 'linking/linking-model';
import { getLocationTracks } from 'track-layout/layout-location-track-api';

type SwitchJointInfobox = {
    switchAlignments: SwitchAlignment[];
    jointConnections: LayoutSwitchJointConnection[];
    topologicalJointConnections?: TopologicalJointConnection[];
    publishType: PublishType;
};

const SwitchJointInfobox: React.FC<SwitchJointInfobox> = ({
    switchAlignments,
    jointConnections,
    topologicalJointConnections,
    publishType,
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
            () => getLocationTracksForJointConnections(publishType, jointConnections),
            [switchAlignments, jointConnections, publishType],
        ),
        useLoader(
            () =>
                getLocationTracks(
                    (topologicalJointConnections ?? []).flatMap(
                        (jointConnection) => jointConnection.locationTrackIds,
                    ),
                    publishType,
                ),
            [],
        ),
    ]
        .flat()
        .filter(filterNotEmpty);

    function getLocationTracksForJointNumbers(jointNumbers: JointNumber[]) {
        const locationTrackIds = getMatchingLocationTrackIdsForJointNumbers(
            jointNumbers,
            jointConnections,
        );
        return getLocationTrackBadges(locationTrackIds);
    }

    function getLocationTrackBadges(locationTrackIds: LocationTrackId[]) {
        return locationTrackIds
            .map((t) => locationTracks?.find((locationTrack) => locationTrack.id === t))
            .filter(filterNotEmpty)
            .map((t) => <LocationTrackBadge key={t.id} locationTrack={t} />);
    }

    return (
        <React.Fragment>
            <dl className={styles['switch-joint-infobox__joints-container']}>
                <dt className={styles['switch-joint-infobox__joint-title']}>
                    {t('tool-panel.switch.layout.joint-alignments-title')}
                </dt>
                <dd className={styles['switch-joint-infobox__joint-title']}>
                    {t('tool-panel.switch.layout.joint-alignments-location-tracks-title')}
                </dd>
                {switchAlignments.map((a) => {
                    return (
                        <React.Fragment key={a.id}>
                            <dt className={styles['switch-joint-infobox__joint-alignments-title']}>
                                {a.jointNumbers.map((j) => switchJointNumberToString(j)).join('-')}
                            </dt>
                            <dd className={styles['switch-joint-infobox__location-tracks']}>
                                <div>{getLocationTracksForJointNumbers(a.jointNumbers)}</div>
                            </dd>
                        </React.Fragment>
                    );
                })}
                {displayedLocationTracksEndingAtJoint.length > 0 && (
                    <>
                        <dt className={styles['switch-joint-infobox__joint-title']}>
                            {t('tool-panel.switch.layout.joint-number-title')}
                        </dt>
                        <dd className={styles['switch-joint-infobox__joint-title']}>
                            {t('tool-panel.switch.layout.location-tracks-end-at-joint-title')}
                        </dd>
                        {displayedLocationTracksEndingAtJoint?.map((a) => {
                            return (
                                <React.Fragment key={a.jointNumber}>
                                    <dt
                                        className={
                                            styles['switch-joint-infobox__joint-points-title']
                                        }>
                                        {switchJointNumberToString(a.jointNumber)}.{' '}
                                        {t('tool-panel.switch.layout.point')}
                                    </dt>
                                    <dd className={styles['switch-joint-infobox__location-tracks']}>
                                        <div>{getLocationTrackBadges(a.locationTrackIds)}</div>
                                    </dd>
                                </React.Fragment>
                            );
                        })}
                    </>
                )}
            </dl>
        </React.Fragment>
    );
};

export default SwitchJointInfobox;
