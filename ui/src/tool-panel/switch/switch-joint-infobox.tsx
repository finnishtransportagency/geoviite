import * as React from 'react';
import { JointNumber, LayoutContext, SwitchStructure } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { combineLocationTrackIds, getLocationTracksEndingAtJoints } from 'linking/linking-utils';
import { useLoader } from 'utils/react-utils';
import { filterNotEmpty, objectEntries } from 'utils/array-utils';
import { switchJointNumberToString } from 'utils/enum-localization-utils';
import { LocationTrackBadge } from 'geoviite-design-lib/alignment/location-track-badge';
import styles from './switch-infobox.scss';
import { getLocationTracks } from 'track-layout/layout-location-track-api';
import { SuggestedSwitch } from 'linking/linking-model';

type SwitchJointInfobox = {
    suggestedSwitch: SuggestedSwitch;
    suggestedSwitchStructure: SwitchStructure;
    layoutContext: LayoutContext;
    onSelectLocationTrackBadge?: (locationTrackId: LocationTrackId) => void;
};

const SwitchJointInfobox: React.FC<SwitchJointInfobox> = ({
    suggestedSwitch,
    suggestedSwitchStructure,
    layoutContext,
    onSelectLocationTrackBadge,
}) => {
    const { t } = useTranslation();
    const locationTracksEndingAtJoint = combineLocationTrackIds(
        suggestedSwitchStructure.alignments.map((switchAlignment) =>
            getLocationTracksEndingAtJoints(
                switchAlignment.jointNumbers,
                suggestedSwitch.trackLinks,
            ),
        ),
    );

    const locationTracks = (
        useLoader(
            () =>
                getLocationTracks(
                    objectEntries(suggestedSwitch.trackLinks)
                        .filter(([_, links]) => links.suggestedLinks !== undefined)
                        .map(([id]) => id),
                    layoutContext,
                ),
            [layoutContext.branch, layoutContext.publicationState],
        ) ?? []
    ).filter(filterNotEmpty);

    const locationTrackBadgeOnClickHandler = (locationTrackId: LocationTrackId) =>
        onSelectLocationTrackBadge ? () => onSelectLocationTrackBadge(locationTrackId) : undefined;

    function getLocationTracksForJointNumbers(jointNumbers: JointNumber[]) {
        const locationTrackIds = jointNumbers.flatMap(
            (j) =>
                locationTracksEndingAtJoint.find(({ jointNumber }) => j === jointNumber)
                    ?.locationTrackIds ?? [],
        );
        return getLocationTrackBadges(locationTrackIds);
    }

    function getLocationTrackBadges(locationTrackIds: LocationTrackId[]) {
        const badges = locationTrackIds
            .map((t) => locationTracks?.find((locationTrack) => locationTrack.id === t))
            .filter(filterNotEmpty)
            .map((t) => (
                <LocationTrackBadge
                    key={t.id}
                    locationTrack={t}
                    onClick={locationTrackBadgeOnClickHandler(t.id)}
                />
            ));

        return badges.length > 0 ? (
            badges
        ) : (
            <span className={styles['switch-joint-infobox__no-alignments']}>
                {t('tool-panel.switch.layout.no-alignments')}
            </span>
        );
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
                {suggestedSwitchStructure.alignments.map((a) => (
                    <React.Fragment key={a.jointNumbers.join('_')}>
                        <dt className={styles['switch-joint-infobox__joint-alignments-title']}>
                            {a.jointNumbers.map((j) => switchJointNumberToString(j)).join('-')}
                        </dt>
                        <dd className={styles['switch-joint-infobox__location-tracks']}>
                            <div>{getLocationTracksForJointNumbers(a.jointNumbers)}</div>
                        </dd>
                    </React.Fragment>
                ))}
                {locationTracksEndingAtJoint.length > 0 && (
                    <React.Fragment>
                        <dt className={styles['switch-joint-infobox__joint-title']}>
                            {t('tool-panel.switch.layout.joint-number-title')}
                        </dt>
                        <dd className={styles['switch-joint-infobox__joint-title']}>
                            {t('tool-panel.switch.layout.location-tracks-end-at-joint-title')}
                        </dd>
                        {locationTracksEndingAtJoint?.map((a) => (
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
        </React.Fragment>
    );
};

export default SwitchJointInfobox;
