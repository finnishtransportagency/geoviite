import { formatTrackMeter } from 'utils/geography-utils';
import * as React from 'react';
import { TrackMeter } from 'common/common-model';

type TrackMeterProps = {
    value?: TrackMeter;
};

const TrackMeter: React.FC<TrackMeterProps> = ({ value }: TrackMeterProps) => {
    return <React.Fragment>{value ? formatTrackMeter(value) : ''}</React.Fragment>;
};

export default TrackMeter;
