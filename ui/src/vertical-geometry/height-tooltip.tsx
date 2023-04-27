import React from 'react';
import { SnappedPoint } from 'vertical-geometry/snapped-point';
import { Coordinates } from 'vertical-geometry/coordinates';
import { formatTrackMeter, formatTrackMeterWithoutMeters } from 'utils/geography-utils';

interface HeightTooltipProps {
    point: SnappedPoint;
    elementPosition: DOMRect;
    coordinates: Coordinates;
}

export const HeightTooltip: React.FC<HeightTooltipProps> = ({
    point,
    elementPosition,
    coordinates,
}) => {
    const displayedAddress =
        point.snapTarget === 'segmentBoundary' ||
        point.snapTarget === 'geometryElement' ||
        (point.snapTarget === 'didNotSnap' && coordinates.mMeterLengthPxOverM > 20)
            ? formatTrackMeter(point.address)
            : formatTrackMeterWithoutMeters(point.address);
    return (
        <div
            className="vertical-geometry-diagram__tooltip"
            style={{
                position: 'absolute',
                left: elementPosition.left + point.xPositionPx + 20,
                top: elementPosition.top + point.yPositionPx + 20,
            }}>
            {displayedAddress}
            <br />
            kt=
            {point.height.toLocaleString(undefined, {
                maximumFractionDigits: 2,
            })}
            {point.fileName && (
                <>
                    <br />
                    {point.fileName}
                </>
            )}
        </div>
    );
};
