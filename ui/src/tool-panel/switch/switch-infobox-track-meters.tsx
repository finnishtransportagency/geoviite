import * as React from 'react';
import styles from './switch-infobox.scss';
import { SwitchJointTrackMeter } from 'track-layout/track-layout-model';
import { JointNumber } from 'common/common-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { LocationTrackLink } from 'tool-panel/location-track/location-track-link';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
import { groupBy } from 'utils/array-utils';
import { useTranslation } from 'react-i18next';
import { switchJointNumberToString } from 'utils/enum-localization-utils';

const formatJointTrackMeter = (jointTrackMeter: SwitchJointTrackMeter) => {
    return (
        <span>
            <TrackMeter value={jointTrackMeter.trackMeter} />
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

    const [otherJointsAddress, setOtherJointsAddress] = React.useState<
        Record<string, SwitchJointTrackMeter[]>
    >({});

    const [presentationJointAddress, setPresentationJointAddress] = React.useState<
        SwitchJointTrackMeter[]
    >([]);

    const [showOtherJoints, setShowOtherJoints] = React.useState(false);

    React.useEffect(() => {
        setPresentationJointAddress(
            jointTrackMeters.filter((jtm) => jtm.jointNumber == presentationJoint),
        );

        setOtherJointsAddress(
            groupBy(
                jointTrackMeters.filter((jtm) => jtm.jointNumber != presentationJoint),
                (i) => i.jointNumber,
            ),
        );

        setShowOtherJoints(false);
    }, [jointTrackMeters, presentationJoint]);

    return (
        <div className="switch-infobox-track-meters">
            <section>
                {presentationJointAddress.length === 0 && (
                    <span>{t('tool-panel.switch.layout.no-location')}</span>
                )}
                <ol className={styles['switch-infobox-track-meters__track-meters']}>
                    {presentationJointAddress.map((pja) => (
                        <li
                            key={pja.locationTrackId}
                            className={styles['switch-infobox-track-meters__track-meter']}>
                            {formatJointTrackMeter(pja)}
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
                                {addresses.map((a) => (
                                    <li
                                        key={a.locationTrackId}
                                        className={
                                            styles['switch-infobox-track-meters__track-meter']
                                        }>
                                        {formatJointTrackMeter(a)}
                                    </li>
                                ))}
                            </ol>
                        </section>
                    );
                })}

            {Object.keys(otherJointsAddress).length > 0 && (
                <section>
                    <Button
                        className={styles['switch-infobox-track-meters__show-more']}
                        size={ButtonSize.SMALL}
                        variant={ButtonVariant.GHOST}
                        onClick={() => setShowOtherJoints(!showOtherJoints)}>
                        {showOtherJoints
                            ? t('tool-panel.switch.layout.show-less')
                            : t('tool-panel.switch.layout.show-more')}
                    </Button>
                </section>
            )}
        </div>
    );
};
