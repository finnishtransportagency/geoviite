import { formatTrackMeter, formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import * as React from 'react';
import { TrackMeter } from 'common/common-model';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import styles from 'geoviite-design-lib/track-meter/track-meter.scss';

type TrackMeterProps = {
    trackMeter?: TrackMeter;
    placeholder?: string;
    onClickAction?: () => void;
    displayDecimals?: boolean;
};

const formatTrackMeterForDisplay = (trackMeter: TrackMeter, displayDecimals: boolean) =>
    displayDecimals ? formatTrackMeter(trackMeter) : formatTrackMeterWithoutMeters(trackMeter);

const TrackMeter: React.FC<TrackMeterProps> = ({
    trackMeter,
    placeholder = '',
    onClickAction,
    displayDecimals = true,
}: TrackMeterProps) => {
    const displayedValue = trackMeter
        ? formatTrackMeterForDisplay(trackMeter, displayDecimals)
        : placeholder;

    return onClickAction ? (
        <span className={styles['track-meter-value-container']}>
            {displayedValue}
            {trackMeter && (
                <a className={styles['position-pin-container']} onClick={onClickAction}>
                    <Icons.Target size={IconSize.SMALL} />
                </a>
            )}
        </span>
    ) : (
        <React.Fragment>{displayedValue}</React.Fragment>
    );
};

export default TrackMeter;
