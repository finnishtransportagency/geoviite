import { formatTrackMeter, formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import * as React from 'react';
import { TrackMeter } from 'common/common-model';
import { Point } from 'model/geometry';

type DisplayedAddressPoint = {
    point: Point;
    address: TrackMeter;
};

type TrackMeterProps = {
    addressPoint?: DisplayedAddressPoint;
    placeholder?: string;
    onShowOnMap?: () => void;
    displayDecimals?: boolean;
};

const TrackMeter: React.FC<TrackMeterProps> = ({
    addressPoint,
    placeholder,
    onShowOnMap,
    displayDecimals = true,
}: TrackMeterProps) => {
    if (!addressPoint || !onShowOnMap) {
        return <React.Fragment>{placeholder ?? ''}</React.Fragment>;
    }

    const displayedTrackMeterValue = displayDecimals
        ? formatTrackMeter(addressPoint.address)
        : formatTrackMeterWithoutMeters(addressPoint.address);

    return (
        <a className={'link'} onClick={onShowOnMap}>
            {displayedTrackMeterValue}
        </a>
    );
};

export default TrackMeter;
