// Horizontal layout of the diagram: the selected location tracks' m-value spans are
// concatenated left to right. The diagram x-axis ("diagram meters") runs from 0 to the
// sum of the track span lengths.

export interface TrackSpanInput {
  // Identifies the displayed span, not the track: a route can visit the same location
  // track more than once, each visit being its own span. For whole-track display the
  // key is simply the track's oid.
  key: string;
  oid: string;
  name: string;
  startM: number; // m-value of the span's start within its display m-frame
  endM: number;
}

export interface TrackSpan extends TrackSpanInput {
  offsetX: number; // diagram-x where this track's span begins
  lengthM: number;
}

export interface Layout {
  spans: TrackSpan[];
  totalLength: number;
}

export interface ViewRange {
  startX: number;
  endX: number;
}

export function computeLayout(inputs: TrackSpanInput[]): Layout {
  const spans: TrackSpan[] = [];
  let offsetX = 0;
  for (const input of inputs) {
    const lengthM = Math.max(0, input.endM - input.startM);
    spans.push({ ...input, offsetX, lengthM });
    offsetX += lengthM;
  }
  return { spans, totalLength: offsetX };
}

export interface TrackPosition {
  key: string; // the displayed span's key
  m: number; // m-value within the span's display m-frame
}

export function xToTrackPosition(
  layout: Layout,
  x: number,
): TrackPosition | undefined {
  for (const span of layout.spans) {
    if (x >= span.offsetX && x <= span.offsetX + span.lengthM) {
      return { key: span.key, m: span.startM + (x - span.offsetX) };
    }
  }
  return undefined;
}

export function trackPositionToX(
  layout: Layout,
  position: TrackPosition,
): number | undefined {
  const span = layout.spans.find((s) => s.key === position.key);
  if (!span || position.m < span.startM || position.m > span.endM) {
    return undefined;
  }
  return span.offsetX + (position.m - span.startM);
}

const minimumViewSpanM = 2;

export function clampView(view: ViewRange, totalLength: number): ViewRange {
  const width = Math.min(
    Math.max(view.endX - view.startX, minimumViewSpanM),
    totalLength,
  );
  let startX = view.startX + (view.endX - view.startX - width) / 2;
  startX = Math.min(Math.max(startX, 0), totalLength - width);
  return { startX, endX: startX + width };
}

// When the set of displayed spans changes, keep the center of the view pointing at the
// same m-value within the same span if that span is still displayed; otherwise keep
// the same diagram-x.
export function remapViewKeepingCenter(
  view: ViewRange,
  oldLayout: Layout,
  newLayout: Layout,
): ViewRange {
  const center = (view.startX + view.endX) / 2;
  const halfWidth = (view.endX - view.startX) / 2;
  const position = xToTrackPosition(oldLayout, center);
  const mappedCenter = position && trackPositionToX(newLayout, position);
  const newCenter =
    mappedCenter ?? Math.min(Math.max(center, 0), newLayout.totalLength);
  return clampView(
    { startX: newCenter - halfWidth, endX: newCenter + halfWidth },
    newLayout.totalLength,
  );
}

export function zoomedView(
  view: ViewRange,
  totalLength: number,
  focusX: number,
  wheelDeltaY: number,
): ViewRange {
  const factor = Math.pow(1.05, wheelDeltaY * 0.01);
  return clampView(
    {
      startX: focusX + (view.startX - focusX) * factor,
      endX: focusX + (view.endX - focusX) * factor,
    },
    totalLength,
  );
}

export function pannedView(
  view: ViewRange,
  totalLength: number,
  deltaXM: number,
): ViewRange {
  const width = view.endX - view.startX;
  const startX = Math.min(
    Math.max(view.startX + deltaXM, 0),
    totalLength - width,
  );
  return { startX, endX: startX + width };
}
