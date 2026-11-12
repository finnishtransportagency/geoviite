import React from "react";
import { Coordinates, heightToY } from "../math/coordinates";

export const lineGridStrokeColor = "#999";
// vertical guidance lines, including the top and bottom lines
const maxVerticalTickCount = 5;
const verticalTickLengthsMeter = [1.0, 2.0, 5.0, 10.0, 25.0];

const heightLabelOffset = 2;
const heightLabelsBackgroundWidthPx = 18;
// essentially, the amount of room we give the plan linking label
const heightLabelsBackgroundStartYPx = 22;

function chooseVerticalTickLength(
  bottomTickHeight: number,
  topTickHeight: number,
): number | undefined {
  const tickHeightIndexR = verticalTickLengthsMeter.findIndex((tickLength) => {
    const minMidTickIndex = Math.ceil(
      (bottomTickHeight + tickLength / 2) / tickLength,
    );
    const maxMidTickIndex = Math.floor(
      (topTickHeight - tickLength / 2) / tickLength,
    );
    return maxMidTickIndex - minMidTickIndex < maxVerticalTickCount;
  });
  return tickHeightIndexR === -1
    ? undefined
    : verticalTickLengthsMeter[tickHeightIndexR];
}

function enumerateInStepsUpTo(
  start: number,
  stepSize: number,
  upTo: number,
): number[] {
  const rv: number[] = [];
  for (let x = start; x <= upTo; x += stepSize) {
    rv.push(x);
  }
  return rv;
}

function heightTicks(coordinates: Coordinates): number[] {
  if (coordinates.topHeightTick <= coordinates.bottomHeightTick) {
    return [];
  }
  const tickLength = chooseVerticalTickLength(
    coordinates.bottomHeightTick,
    coordinates.topHeightTick,
  );
  return tickLength === undefined
    ? [coordinates.bottomHeightTick, coordinates.topHeightTick]
    : (() => {
        const firstMidTick =
          tickLength * Math.ceil(coordinates.bottomHeightTick / tickLength);
        return [
          coordinates.bottomHeightTick,
          ...enumerateInStepsUpTo(
            firstMidTick,
            tickLength,
            coordinates.topHeightTick,
          ),
          coordinates.topHeightTick,
        ];
      })();
}

export interface HeightLinesProps {
  coordinates: Coordinates;
}

export const HeightLabels: React.FC<HeightLinesProps> = ({ coordinates }) => (
  <>
    <rect
      x={0}
      y={heightLabelsBackgroundStartYPx}
      width={heightLabelsBackgroundWidthPx + heightLabelOffset * 2}
      height={
        heightToY(coordinates, coordinates.bottomHeightTick) -
        heightLabelsBackgroundStartYPx
      }
      stroke="none"
      fill="white"
      opacity={0.85}
    />
    {heightTicks(coordinates).map((heightMeters, i) => {
      const heightPx = heightToY(coordinates, heightMeters);
      return (
        <React.Fragment key={i}>
          <line
            x1={0}
            x2={heightLabelsBackgroundWidthPx + heightLabelOffset * 2}
            y1={heightPx}
            y2={heightPx}
            stroke={lineGridStrokeColor}
            fill="none"
            shapeRendering="crispEdges"
          />
          <text x={heightLabelOffset} y={heightPx - 2}>
            {heightMeters}
          </text>
        </React.Fragment>
      );
    })}
  </>
);

export const HeightLines: React.FC<HeightLinesProps> = ({ coordinates }) => (
  <>
    {heightTicks(coordinates).map((heightMeters, i) => {
      const heightPx = heightToY(coordinates, heightMeters);
      return (
        <line
          key={i}
          x1={0}
          x2={coordinates.dimensions.widthPx}
          y1={heightPx}
          y2={heightPx}
          stroke={lineGridStrokeColor}
          fill="none"
          shapeRendering="crispEdges"
        />
      );
    })}
  </>
);
