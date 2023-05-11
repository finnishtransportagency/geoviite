import React from 'react';
import { formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import { Translate } from 'vertical-geometry/translate';
import { lineGridStrokeColor } from 'vertical-geometry/height-lines';
import { Coordinates, mToX } from 'vertical-geometry/coordinates';
import { TrackKmHeights } from 'geometry/geometry-api';
import {
    minimumInterval,
    minimumIntervalOrLongest,
    minimumLabeledTickDistancePx,
} from 'vertical-geometry/ticks-at-intervals';

export interface LabeledTicksProps {
    trackKmHeights: TrackKmHeights[];
    coordinates: Coordinates;
}

const TickLabel: React.FC<{
    m: number;
    meter: number;
    kmNumber: string;
    coordinates: Coordinates;
}> = ({ m, kmNumber, meter, coordinates }) => {
    const x = mToX(coordinates, m);
    return (
        <>
            <Translate x={x - 50} y={coordinates.fullDiagramHeightPx - (meter === 0 ? 3 : 8)}>
                <text scale="0.7 0.7">
                    KM{' '}
                    {formatTrackMeterWithoutMeters({
                        kmNumber,
                        meters: meter,
                    })}
                </text>
                <line y={-40} stroke="black" />
            </Translate>
            <line
                x1={x}
                x2={x}
                y1={0}
                y2={coordinates.fullDiagramHeightPx - 20}
                stroke={lineGridStrokeColor}
                fill="none"
                shapeRendering="crispEdges"
            />
        </>
    );
};

export const LabeledTicks: React.FC<LabeledTicksProps> = ({ trackKmHeights, coordinates }) => {
    // avoid rendering too far off the screen for performance
    const startM = coordinates.startM - 80 / coordinates.mMeterLengthPxOverM;
    const endM = coordinates.endM + 25 / coordinates.mMeterLengthPxOverM;

    // rough approximations based on m-meter = track meter, and track km = 1000 meters
    const trackKmLabelDisplayStep = minimumIntervalOrLongest(
        1000 * coordinates.mMeterLengthPxOverM,
        minimumLabeledTickDistancePx,
    );
    const trackMeterDisplayStep = minimumInterval(
        coordinates.mMeterLengthPxOverM,
        minimumLabeledTickDistancePx,
    );

    return (
        <>
            {trackKmHeights
                .filter(
                    ({ kmNumber }) =>
                        trackKmLabelDisplayStep === 1 ||
                        parseInt(kmNumber) % trackKmLabelDisplayStep === 0,
                )
                .flatMap(({ kmNumber, trackMeterHeights }, trackKmIndex) =>
                    trackMeterHeights
                        .filter(({ m, meter }, meterIndex) => {
                            const ordinaryTick = Number.isInteger(meter);
                            const inRenderedRange = m > startM && m < endM;
                            const hitsMeterStep =
                                meter === 0 ||
                                (trackMeterDisplayStep !== undefined &&
                                    meter % trackMeterDisplayStep === 0);
                            const hasSpaceBeforeNextKm =
                                trackKmIndex === trackKmHeights.length - 1 ||
                                meterIndex === 0 ||
                                (trackKmHeights[trackKmIndex + 1].trackMeterHeights[0].m - m) *
                                    coordinates.mMeterLengthPxOverM >
                                    minimumLabeledTickDistancePx;
                            return (
                                ordinaryTick &&
                                inRenderedRange &&
                                hitsMeterStep &&
                                hasSpaceBeforeNextKm
                            );
                        })
                        .map(({ m, meter }, meterIndex) => (
                            <TickLabel
                                key={`${trackKmIndex}_${meterIndex}`}
                                m={m}
                                meter={meter}
                                kmNumber={kmNumber}
                                coordinates={coordinates}
                            />
                        )),
                )}
        </>
    );
};
