import React, { useRef, useState } from 'react';
import { SnappedPoint } from 'vertical-geometry/snapped-point';
import { Coordinates } from 'vertical-geometry/coordinates';
import { formatTrackMeter, formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import styles from './vertical-geometry-diagram.scss';
import { useResizeObserver } from 'utils/use-resize-observer';
import { formatRounded } from 'utils/string-utils';

interface HeightTooltipProps {
    point: SnappedPoint;
    parentElementRect: DOMRect;
    coordinates: Coordinates;
}

export const HeightTooltip: React.FC<HeightTooltipProps> = ({
    point,
    parentElementRect,
    coordinates,
}) => {
    const displayedAddress =
        point.snapTarget === 'segmentBoundary' ||
        point.snapTarget === 'geometryElement' ||
        (point.snapTarget === 'didNotSnap' && coordinates.mMeterLengthPxOverM > 20)
            ? formatTrackMeter(point.address)
            : formatTrackMeterWithoutMeters(point.address);
    const ref = useRef<HTMLDivElement>(null);
    const [width, setWidth] = useState(0);
    const [height, setHeight] = useState(0);

    useResizeObserver({
        ref,
        onResize: () => {
            const boundingClientRect = ref.current?.getBoundingClientRect();
            if (boundingClientRect) {
                setWidth(boundingClientRect.width);
                setHeight(boundingClientRect.height);
            }
        },
    });

    return (
        <div
            ref={ref}
            className={styles['vertical-geometry-diagram__tooltip']}
            style={{
                position: 'fixed',
                left: Math.min(
                    parentElementRect.left + point.xPositionPx + 20,
                    parentElementRect.right - width,
                ),
                top: Math.min(
                    parentElementRect.top + point.yPositionPx + 20,
                    parentElementRect.bottom - height,
                ),
            }}>
            {displayedAddress}
            <br />
            kt=
            {formatRounded(point.height, 2)}
            {point.fileName && (
                <>
                    <br />
                    {point.fileName}
                </>
            )}
        </div>
    );
};
