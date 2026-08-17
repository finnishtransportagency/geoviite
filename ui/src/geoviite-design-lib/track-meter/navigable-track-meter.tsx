import * as React from 'react';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
import { TrackMeter as TrackMeterValue } from 'common/common-model';
import { Point } from 'model/geometry';
import {
    calculateBoundingBoxToShowAroundLocation,
    MAP_POINT_DEFAULT_BBOX_OFFSET,
} from 'map/map-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';

type NavigableTrackMeterProps = {
    trackMeter?: TrackMeterValue;
    location?: Point;
    placeholder?: string;
    mapNavigationBboxOffset?: number;
    displayDecimals?: boolean;
};

const NavigableTrackMeter: React.FC<NavigableTrackMeterProps> = ({
    trackMeter,
    location,
    placeholder,
    mapNavigationBboxOffset = MAP_POINT_DEFAULT_BBOX_OFFSET,
    displayDecimals,
}: NavigableTrackMeterProps) => {
    const showArea = React.useMemo(() => createDelegates(trackLayoutActionCreators).showArea, []);

    const showLocationOnMap =
        location &&
        (() => {
            showArea(calculateBoundingBoxToShowAroundLocation(location, mapNavigationBboxOffset));
        });

    return (
        <TrackMeter
            onClickAction={showLocationOnMap}
            trackMeter={trackMeter}
            displayDecimals={displayDecimals}
            placeholder={placeholder}
        />
    );
};

export default NavigableTrackMeter;
