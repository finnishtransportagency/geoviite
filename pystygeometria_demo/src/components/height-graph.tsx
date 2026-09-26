import React from "react";
import {
  Coordinates,
  heightToY,
  mToX,
  polylinePoints,
} from "../math/coordinates";
import { NeighborPvi, profileHeightAtM, PviItem } from "../math/profile";

export interface HeightGraphProps {
  items: PviItem[];
  coordinates: Coordinates;
  trackStartM: number;
  trackEndM: number;
  // Nearest PVIs carried from the neighbouring tracks; let a track with no PVIs of its
  // own draw the grade line continuing in from a neighbour.
  prevItem?: NeighborPvi;
  nextItem?: NeighborPvi;
}

export const HeightGraph: React.FC<HeightGraphProps> = ({
  items,
  coordinates,
  trackStartM,
  trackEndM,
  prevItem,
  nextItem,
}) => {
  // A track with no PVIs of its own can still be drawn from a neighbouring PVI; only
  // bail out when there is nothing at all to derive a height from.
  if (items.length === 0 && !prevItem && !nextItem) {
    return <React.Fragment />;
  }

  const startM = Math.max(coordinates.startM, trackStartM);
  const endM = Math.min(coordinates.endM, trackEndM);
  if (startM >= endM) {
    return <React.Fragment />;
  }

  // One sample per pixel keeps the parabolic curve sections smooth; the visible span is
  // at most the diagram width, so this is bounded regardless of zoom.
  const stepM =
    coordinates.mMeterLengthPxOverM > 0
      ? 1 / coordinates.mMeterLengthPxOverM
      : endM - startM;

  const lines: React.JSX.Element[] = [];
  let linePoints: [number, number][] = [];
  let lineIndex = 0;
  const finishLineIfStarted = () => {
    if (linePoints.length > 0) {
      lines.push(
        <polyline
          key={lineIndex++}
          points={polylinePoints(linePoints)}
          stroke="black"
          fill="none"
          strokeWidth={2}
        />,
      );
      linePoints = [];
    }
  };

  const sample = (m: number) => {
    const height = profileHeightAtM(items, m, prevItem, nextItem);
    if (height === undefined) {
      finishLineIfStarted();
    } else {
      linePoints.push([mToX(coordinates, m), heightToY(coordinates, height)]);
    }
  };

  for (let m = startM; m < endM; m += stepM) {
    sample(m);
  }
  // Always anchor the final point at the visible end so the curve reaches the edge.
  sample(endM);
  finishLineIfStarted();

  return <>{lines}</>;
};
