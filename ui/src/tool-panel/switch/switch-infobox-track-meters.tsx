import * as React from 'react';
import { LocationTrackLink } from 'tool-panel/location-track/location-track-link';
import styles from './switch-infobox.scss';
import { Precision, roundToPrecision } from 'utils/rounding';
import { SwitchTrackMeter } from 'track-layout/track-layout-model';

type TrackMeterProps = {
    switchTrackMeter: SwitchTrackMeter;
    precision?: Precision.kmNumberMeters | Precision.kmNumberMillimeters;
};

const TrackMeter: React.FC<TrackMeterProps> = ({
    switchTrackMeter,
    precision = Precision.kmNumberMeters,
}) => {
    return (
        <>
            {`${switchTrackMeter.trackMeter.kmNumber} + ${roundToPrecision(
                switchTrackMeter.trackMeter.meters,
                precision,
            )} (`}
            <LocationTrackLink
                key={switchTrackMeter.locationTrackId}
                locationTrackId={switchTrackMeter.locationTrackId}
                locationTrackName={switchTrackMeter.name}
            />
            {')'}
        </>
    );
};

export type SwitchInfoboxTrackMetersProps = {
    switchTrackMeters: SwitchTrackMeter[] | undefined;
};

export const SwitchInfoboxTrackMeters: React.FC<SwitchInfoboxTrackMetersProps> = ({
    switchTrackMeters,
}: SwitchInfoboxTrackMetersProps) => {
    return (
        <React.Fragment>
            {switchTrackMeters && (
                <ul className={styles['switch-infobox-track-meters__ul']}>
                    {switchTrackMeters.map((switchTrackMeter) => (
                        <li key={switchTrackMeter.locationTrackId}>
                            <TrackMeter
                                switchTrackMeter={switchTrackMeter}
                                precision={Precision.kmNumberMillimeters}
                            />
                        </li>
                    ))}
                </ul>
            )}
        </React.Fragment>
    );
};
