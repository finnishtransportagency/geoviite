import { formatTrackMeter, formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import * as React from 'react';
import { TrackMeter } from 'common/common-model';

type TrackMeterProps = {
    trackMeter?: TrackMeter;
    placeholder?: string;
    onShowOnMap?: () => void;
    displayDecimals?: boolean;
};

const formatTrackMeterForDisplay = (trackMeter: TrackMeter, displayDecimals: boolean) =>
    displayDecimals ? formatTrackMeter(trackMeter) : formatTrackMeterWithoutMeters(trackMeter);

const TrackMeter: React.FC<TrackMeterProps> = ({
    trackMeter,
    placeholder = '',
    onShowOnMap,
    displayDecimals = true,
}: TrackMeterProps) => {
    const displayedValue = trackMeter
        ? formatTrackMeterForDisplay(trackMeter, displayDecimals)
        : placeholder;

    return onShowOnMap ? (
        <a className={'link'} onClick={onShowOnMap}>
            {displayedValue}
        </a>
    ) : (
        <React.Fragment>{displayedValue}</React.Fragment>
    );
};

export default TrackMeter;
