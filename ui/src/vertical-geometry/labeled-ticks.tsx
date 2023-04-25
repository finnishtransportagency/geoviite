import React from 'react';
import { formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import { Translate } from 'vertical-geometry/translate';
import { lineGridStrokeColor } from 'vertical-geometry/height-lines';
import { Coordinates, mToX } from 'vertical-geometry/coordinates';
import { TrackKmHeights } from 'geometry/geometry-api';

const labeledTicksEveryNthHorizontalTick = 10;
const labeledTickKmCounts = [1, 2, 5, 10, 25, 100, 250];
const minimumLabeledTickDistancePx = 100;
const minPixelsBeforeLastLabeledTickOnKm = 10;
export interface LabeledTicksProps {
    trackKmHeights: TrackKmHeights[];
    coordinates: Coordinates;
}

export const LabeledTicks: React.FC<LabeledTicksProps> = ({ trackKmHeights, coordinates }) => {
    // avoid rendering too far off the screen for performance
    const startM = coordinates.startM - 80 / coordinates.mMeterLengthPxOverM;
    const endM = coordinates.endM + 25 / coordinates.mMeterLengthPxOverM;

    // a rough approximation assuming track kilometers are 1000 m-value meters long is fine enough here
    const lengthOfTrackKmInPixels = 1000 * coordinates.mMeterLengthPxOverM;
    const labelsEveryNKilometers =
        labeledTickKmCounts.find(
            (kmCount) => kmCount * lengthOfTrackKmInPixels > minimumLabeledTickDistancePx,
        ) ?? labeledTickKmCounts[labeledTickKmCounts.length - 1];

    return (
        <>
            {trackKmHeights.flatMap(({ kmNumber, trackMeterHeights }, trackKmIndex) =>
                trackMeterHeights
                    .filter(({ meter }) => meter % coordinates.horizontalTickLengthMeters === 0)
                    .filter(
                        ({ m }, i) =>
                            trackKmIndex % labelsEveryNKilometers === 0 &&
                            i % labeledTicksEveryNthHorizontalTick === 0 &&
                            m > startM &&
                            m < endM &&
                            (i == 0 ||
                                trackKmIndex === trackKmHeights.length - 1 ||
                                (trackKmHeights[trackKmIndex + 1].trackMeterHeights[0].m - m) *
                                    coordinates.mMeterLengthPxOverM >
                                    minPixelsBeforeLastLabeledTickOnKm),
                    )
                    .map(({ m, meter }) => {
                        const x = mToX(coordinates, m);
                        return (
                            <React.Fragment key={`${kmNumber}_${meter}`}>
                                <Translate x={x - 25} y={280 + (meter === 0 ? 17 : 12)}>
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
                                    y2={280}
                                    stroke={lineGridStrokeColor}
                                    fill="none"
                                    shapeRendering="crispEdges"
                                />
                            </React.Fragment>
                        );
                    }),
            )}
        </>
    );
};
