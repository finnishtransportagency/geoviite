// Pixel-space mapping for the diagram, ported from Geoviite's vertical-geometry
// coordinates. startM/endM are in whatever m-space the consumer works in: the diagram
// component uses diagram meters, and per-track rendering uses a track-local shift of the
// same coordinates (see withTrackLocalM).

// All the sizes and positions that shape the diagram, in one place so an embedding site
// can pass its own (see the Diagram component's `dimensions` prop). Everything else in
// the drawing derives from these plus the data.
export interface DiagramDimensions {
  widthPx: number;
  heightPx: number;
  // Headroom kept above the highest drawn profile height. This is where the PVI assist
  // line (pviAssistLineHeightPx above the profile) and its rotated labels live, so it
  // must comfortably exceed pviAssistLineHeightPx.
  topHeightPaddingPx: number;
  // Extra space kept below the lowest drawn profile height, above the chart's bottom
  // edge.
  bottomHeightPaddingPx: number;
  // Space reserved between the chart's bottom edge and the diagram's bottom edge; the
  // operational point row is drawn here.
  chartBottomPaddingPx: number;
  // How far above the track profile the PVI assist line is drawn.
  pviAssistLineHeightPx: number;
  // Distance of the operational point dot row below the chart's bottom edge; must fit
  // within chartBottomPaddingPx.
  operationalPointRowOffsetPx: number;
}

export const defaultDiagramDimensions: DiagramDimensions = {
  widthPx: 1200,
  heightPx: 520,
  topHeightPaddingPx: 150,
  bottomHeightPaddingPx: 0,
  chartBottomPaddingPx: 50,
  pviAssistLineHeightPx: 40,
  operationalPointRowOffsetPx: 14,
};

export interface Coordinates {
  dimensions: DiagramDimensions;
  startM: number;
  endM: number;
  mMeterLengthPxOverM: number; // horizontal px per meter
  meterHeightPx: number; // vertical px per meter of height
  bottomHeightTick: number;
  topHeightTick: number;
  chartHeightPx: number; // y of the chart's bottom edge (diagram height minus bottom padding)
}

export function mToX(coordinates: Coordinates, m: number): number {
  return (m - coordinates.startM) * coordinates.mMeterLengthPxOverM;
}

export function xToM(coordinates: Coordinates, x: number): number {
  return x / coordinates.mMeterLengthPxOverM + coordinates.startM;
}

export function heightToY(coordinates: Coordinates, height: number): number {
  return (
    coordinates.chartHeightPx -
    coordinates.dimensions.bottomHeightPaddingPx +
    (coordinates.bottomHeightTick - height) * coordinates.meterHeightPx
  );
}

export function zeroSafeDivision(a: number, b: number): number {
  return b === 0 ? 0 : a / b;
}

export function makeCoordinates(
  dimensions: DiagramDimensions,
  startM: number,
  endM: number,
  heightBounds: [number, number],
): Coordinates {
  const [bottomHeightTick, topHeightTick] = heightBounds;
  return {
    dimensions,
    startM,
    endM,
    mMeterLengthPxOverM: zeroSafeDivision(dimensions.widthPx, endM - startM),
    meterHeightPx: zeroSafeDivision(
      dimensions.heightPx -
        dimensions.topHeightPaddingPx +
        dimensions.bottomHeightPaddingPx,
      topHeightTick - bottomHeightTick,
    ),
    bottomHeightTick,
    topHeightTick,
    chartHeightPx: dimensions.heightPx - dimensions.chartBottomPaddingPx,
  };
}

// The same view, but with startM/endM expressed in a single track's own chainage, so
// that per-track geometry can be rendered with plain mToX/heightToY using track m-values.
export function withTrackLocalM(
  coordinates: Coordinates,
  trackOffsetX: number,
  trackStartM: number,
): Coordinates {
  const shift = trackStartM - trackOffsetX;
  return {
    ...coordinates,
    startM: coordinates.startM + shift,
    endM: coordinates.endM + shift,
  };
}

export function polylinePoints(
  points: readonly (readonly [number, number])[],
): string {
  return points.map(([x, y]) => `${x},${y}`).join(" ");
}

// Integer height bounds (floor/ceil) of a set of height samples; [0, 100] when there is
// nothing to show, like the original diagram's fallback.
export function heightBoundsOf(heights: number[]): [number, number] {
  if (heights.length === 0) {
    return [0, 100];
  }
  const bottom = Math.floor(Math.min(...heights));
  const top = Math.ceil(Math.max(...heights));
  return bottom === top ? [bottom, top + 1] : [bottom, top];
}
