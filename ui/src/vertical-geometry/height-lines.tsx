import React from 'react';
import {
    Coordinates,
    enumerateInStepsUpTo,
    heightToY,
} from 'vertical-geometry/vertical-geometry-diagram';

export const lineGridStrokeColor = '#999';
// vertical guidance lines, including the top and bottom lines
const maxVerticalTickCount = 5;
const verticalTickLengthsMeter = [1.0, 2.0, 5.0, 10.0, 25.0];

function chooseVerticalTickLength(bottomTickHeight: number, topTickHeight: number): number | null {
    // we want to find the tick height giving the maximum number of vertical ticks that's at most the max count
    const tickHeightIndexR = verticalTickLengthsMeter.findIndex((tickLength) => {
        // the indices that the lowest and highest ticks would have, if we were counting up from zero by tickLength
        // meters each time
        const minMidTickIndex = Math.ceil((bottomTickHeight + tickLength / 2) / tickLength);
        const maxMidTickIndex = Math.floor((topTickHeight - tickLength / 2) / tickLength);
        return maxMidTickIndex - minMidTickIndex < maxVerticalTickCount;
    });
    return tickHeightIndexR == -1 ? null : verticalTickLengthsMeter[tickHeightIndexR];
}

function makeHeightLines(coordinates: Coordinates): JSX.Element[] {
    const tickLength = chooseVerticalTickLength(
        coordinates.bottomHeightTick,
        coordinates.topHeightTick,
    );
    const heightTicks =
        tickLength == null
            ? [coordinates.bottomHeightTick, coordinates.topHeightTick]
            : (() => {
                  const firstMidTick =
                      tickLength * Math.ceil(coordinates.bottomHeightTick / tickLength);
                  return [
                      coordinates.bottomHeightTick,
                      // todo: enumerateInStepsUpTo is inclusive, so we might return the top tick twice
                      ...enumerateInStepsUpTo(firstMidTick, tickLength, coordinates.topHeightTick),
                      coordinates.topHeightTick,
                  ];
              })();
    return heightTicks.map((heightMeters, i) => {
        const heightPx = heightToY(coordinates, heightMeters);
        return (
            <React.Fragment key={i}>
                <line
                    x1={0}
                    x2={coordinates.diagramWidthPx}
                    y1={heightPx}
                    y2={heightPx}
                    stroke={lineGridStrokeColor}
                    fill="none"
                    shapeRendering="crispEdges"
                />
                <text x={0} y={heightPx - 2}>
                    {heightMeters}
                </text>
            </React.Fragment>
        );
    });
}

export interface HeightLinesProps {
    coordinates: Coordinates;
}

export const HeightLines: React.FC<HeightLinesProps> = ({ coordinates }) => (
    <React.Fragment>{makeHeightLines(coordinates)}</React.Fragment>
);
