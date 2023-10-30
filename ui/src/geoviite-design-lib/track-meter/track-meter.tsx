import { formatTrackMeter } from 'utils/geography-utils';
import * as React from 'react';
import { TrackMeter } from 'common/common-model';

type TrackMeterProps = {
    value?: TrackMeter;
    placeholder?: string;
};

const TrackMeter: React.FC<TrackMeterProps> = ({ value, placeholder }: TrackMeterProps) => {
    return <React.Fragment>{value ? formatTrackMeter(value) : placeholder ?? ''}</React.Fragment>;
};

export default TrackMeter;
