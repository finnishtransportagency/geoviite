import React from "react";
import { Coordinates, mToX } from "../math/coordinates";
import { Layout, trackPositionToX } from "../math/layout";
import { PlacedOperationalPoint } from "../math/operational-points";

// Operational points drawn along the bottom of the diagram: a small dot at the point's
// chainage on its track, with the name beside it. Points whose track is not displayed,
// or that fall outside the visible horizontal range, are skipped.

const dotRadiusPx = 3;

export interface OperationalPointMarkersProps {
  operationalPoints: PlacedOperationalPoint[];
  layout: Layout;
  coordinates: Coordinates;
}

export const OperationalPointMarkers: React.FC<
  OperationalPointMarkersProps
> = ({ operationalPoints, layout, coordinates }) => {
  const y =
    coordinates.chartHeightPx +
    coordinates.dimensions.operationalPointRowOffsetPx;
  const diagramMs = operationalPoints.map((point) => {
    return trackPositionToX(layout, {
      oid: point.trackOid,
      m: point.m,
    });
  });
  return (
    <>
      {operationalPoints.map((point, index) => {
        const diagramM = diagramMs[index];
        if (diagramM === undefined) {
          return undefined;
        }
        const x = mToX(coordinates, diagramM);
        if (x < 0 || x > coordinates.dimensions.widthPx) {
          return undefined;
        }

        return (
          <g key={point.oid}>
            <circle
              cx={x}
              cy={y}
              r={dotRadiusPx}
              fill="black"
              stroke="white"
              strokeWidth={1}
            />
            <text
              className="diagram-text-stroke-wide"
              x={x + dotRadiusPx + 3}
              y={y}
              dominantBaseline="middle"
            >
              {point.name}
            </text>
          </g>
        );
      })}
    </>
  );
};
