import * as React from 'react';
import styles from './switch-infobox.scss';
import { SwitchJointTrackMeter } from 'track-layout/track-layout-model';
import { JointNumber } from 'common/common-model';
import { LocationTrackLink } from 'tool-panel/location-track/location-track-link';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
import { groupBy } from 'utils/array-utils';
import { useTranslation } from 'react-i18next';
import { switchJointNumberToString } from 'utils/enum-localization-utils';
import { ShowMoreButton } from 'show-more-button/show-more-button';
import { BoundingBox } from 'model/geometry';
import {
    calculateBoundingBoxToShowAroundLocation,
    MAP_POINT_CLOSEUP_BBOX_OFFSET,
} from 'map/map-utils';

const formatJointTrackMeter = (
    jointTrackMeter: SwitchJointTrackMeter,
    addressPlaceHolder: string,
    showArea: (area: BoundingBox) => void,
) => {
    return (
        <span>
            {jointTrackMeter.trackMeter && (
                <TrackMeter
                    onClickAction={() =>
                        showArea(
                            calculateBoundingBoxToShowAroundLocation(
                                jointTrackMeter.location,
                                MAP_POINT_CLOSEUP_BBOX_OFFSET,
                            ),
                        )
                    }
                    trackMeter={jointTrackMeter.trackMeter}
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
    showArea: (area: BoundingBox) => void;
};

export const SwitchInfoboxTrackMeters: React.FC<SwitchInfoboxTrackMetersProps> = ({
    jointTrackMeters,
    presentationJoint,
    showArea,
}: SwitchInfoboxTrackMetersProps) => {
    const { t } = useTranslation();

    const [showOtherJoints, setShowOtherJoints] = React.useState(false);

    const presentationJointAddress = jointTrackMeters.filter(
        (jtm) => jtm.jointNumber == presentationJoint,
    );
    const otherJointsAddress = groupBy(
        jointTrackMeters.filter((jtm) => jtm.jointNumber != presentationJoint),
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
                            {formatJointTrackMeter(pja, addressMissingText, showArea)}
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
                                        {formatJointTrackMeter(a, addressMissingText, showArea)}
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
