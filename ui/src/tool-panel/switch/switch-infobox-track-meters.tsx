import * as React from 'react';
import styles from './switch-infobox.scss';
import { SwitchJointTrackMeter } from 'track-layout/track-layout-model';
import { JointNumber } from 'common/common-model';
import { LocationTrackLink } from 'tool-panel/location-track/location-track-link';
import { groupBy } from 'utils/array-utils';
import { useTranslation } from 'react-i18next';
import { switchJointNumberToString } from 'utils/enum-localization-utils';
import { ShowMoreButton } from 'show-more-button/show-more-button';
import { MAP_POINT_CLOSEUP_BBOX_OFFSET } from 'map/map-utils';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';

const formatJointTrackMeter = (
    jointTrackMeter: SwitchJointTrackMeter,
    addressPlaceHolder: string,
) => {
    return (
        <span>
            {jointTrackMeter.trackMeter && (
                <NavigableTrackMeter
                    trackMeter={jointTrackMeter.trackMeter}
                    location={jointTrackMeter.location}
                    mapNavigationBboxOffset={MAP_POINT_CLOSEUP_BBOX_OFFSET}
                    placeholder={addressPlaceHolder}
                />
            )}
            <br />
            <LocationTrackLink
                locationTrackId={jointTrackMeter.locationTrackId}
                locationTrackName={jointTrackMeter.locationTrackName}
            />
        </span>
    );
};

export type SwitchInfoboxTrackMetersProps = {
    jointTrackMeters: SwitchJointTrackMeter[];
    presentationJoint?: JointNumber;
};

export const SwitchInfoboxTrackMeters: React.FC<SwitchInfoboxTrackMetersProps> = ({
    jointTrackMeters,
    presentationJoint,
}: SwitchInfoboxTrackMetersProps) => {
    const { t } = useTranslation();

    const [showOtherJoints, setShowOtherJoints] = React.useState(false);

    const presentationJointAddress = jointTrackMeters.filter(
        (jtm) => jtm.jointNumber === presentationJoint,
    );
    const otherJointsAddress = groupBy(
        jointTrackMeters.filter((jtm) => jtm.jointNumber !== presentationJoint),
        (i) => i.jointNumber,
    );

    React.useEffect(() => {
        setShowOtherJoints(false);
    }, [jointTrackMeters, presentationJoint]);

    const addressMissingText = t('tool-panel.switch.layout.no-location');

    return (
        <div className="switch-infobox-track-meters">
            <section>
                {presentationJointAddress.length === 0 && <span>{addressMissingText}</span>}
                <ol className={styles['switch-infobox-track-meters__track-meters']}>
                    {presentationJointAddress.map((pja) => (
                        <li
                            key={pja.locationTrackId}
                            className={styles['switch-infobox-track-meters__track-meter']}>
                            {formatJointTrackMeter(pja, addressMissingText)}
                        </li>
                    ))}
                </ol>
            </section>

            {showOtherJoints &&
                Object.entries(otherJointsAddress).map(([jointNumber, addresses]) => {
                    return (
                        <section key={jointNumber}>
                            <h3 className={styles['switch-infobox-track-meters__joint-number']}>
                                {t('tool-panel.switch.layout.joint-number', {
                                    number: switchJointNumberToString(jointNumber),
                                })}
                            </h3>
                            <ol className={styles['switch-infobox-track-meters__track-meters']}>
                                {addresses.map((a: SwitchJointTrackMeter) => (
                                    <li
                                        key={a.locationTrackId}
                                        className={
                                            styles['switch-infobox-track-meters__track-meter']
                                        }>
                                        {formatJointTrackMeter(a, addressMissingText)}
                                    </li>
                                ))}
                            </ol>
                        </section>
                    );
                })}

            {Object.keys(otherJointsAddress).length > 0 && (
                <section>
                    <div className={styles['switch-infobox-track-meters__show-more']}>
                        <ShowMoreButton
                            onShowMore={() => setShowOtherJoints(!showOtherJoints)}
                            expanded={showOtherJoints}
                        />
                    </div>
                </section>
            )}
        </div>
    );
};
