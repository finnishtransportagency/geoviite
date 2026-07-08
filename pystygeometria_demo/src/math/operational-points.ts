import { ExtMeasureAddressPoint, ExtOperationalPoint } from "../api/types";

// An operational point placed onto a single displayed span at the display-frame
// m-value of the nearest span geometry point.
export interface PlacedOperationalPoint {
  oid: string;
  name: string;
  trackKey: string; // the displayed span's key
  m: number; // m-value within the span's display m-frame
}

// One displayed span's geometry, as needed for placing operational points.
export interface OperationalPointTrack {
  key: string;
  geometryPoints: readonly ExtMeasureAddressPoint[];
}

// Operational points farther than this from every track point are not shown. The match
// is to the nearest sampled geometry point (not the lines between them), which is plenty
// precise: operational points are constructions far larger than the sampling interval.
export const MAX_OPERATIONAL_POINT_DISTANCE_M = 100;

const cellSize = MAX_OPERATIONAL_POINT_DISTANCE_M;

// Packs a cell coordinate pair into one number. Collisions would be harmless (they only
// add candidates that the exact distance check then rejects), but with coordinates in
// meters no real-world extent gets anywhere near producing one.
const cellKey = (cellX: number, cellY: number) => cellX * 0x200000 + cellY;

interface IndexedOperationalPoint {
  oid: string;
  x: number;
  y: number;
}

// Spatial index over the operational points: a grid of
// MAX_OPERATIONAL_POINT_DISTANCE_M-sized cells where each point is listed in its own
// cell and the eight around it. Any location within range of an operational point then
// finds it by looking in the location's own cell alone.
export type OperationalPointIndex = Map<number, IndexedOperationalPoint[]>;

export function indexOperationalPoints(
  operationalPoints: readonly ExtOperationalPoint[],
): OperationalPointIndex {
  const index: OperationalPointIndex = new Map();
  for (const op of operationalPoints) {
    const location = op.sijainti;
    if (!location) {
      continue;
    }
    const cellX = Math.floor(location.x / cellSize);
    const cellY = Math.floor(location.y / cellSize);
    const indexed = {
      oid: op.toiminnallinen_piste_oid,
      x: location.x,
      y: location.y,
    };
    for (let dx = -1; dx <= 1; dx++) {
      for (let dy = -1; dy <= 1; dy++) {
        const key = cellKey(cellX + dx, cellY + dy);
        const cell = index.get(key);
        if (cell) {
          cell.push(indexed);
        } else {
          index.set(key, [indexed]);
        }
      }
    }
  }
  return index;
}

// One track's match for an operational point within range of it: the track's nearest
// geometry point.
export interface TrackOperationalPointMatch {
  m: number;
  distanceSq: number;
}

// Finds, for one track on its own, the operational points within
// MAX_OPERATIONAL_POINT_DISTANCE_M of it, each with the track's nearest geometry
// point. Keyed by operational point oid.
export function matchTrackOperationalPoints(
  track: OperationalPointTrack,
  index: OperationalPointIndex,
): Map<string, TrackOperationalPointMatch> {
  const maxDistanceSq =
    MAX_OPERATIONAL_POINT_DISTANCE_M * MAX_OPERATIONAL_POINT_DISTANCE_M;
  const matches = new Map<string, TrackOperationalPointMatch>();
  for (const point of track.geometryPoints) {
    const key = cellKey(
      Math.floor(point.x / cellSize),
      Math.floor(point.y / cellSize),
    );
    const cell = index.get(key);
    if (!cell) {
      continue;
    }
    for (const op of cell) {
      const dx = point.x - op.x;
      const dy = point.y - op.y;
      const distanceSq = dx * dx + dy * dy;
      if (distanceSq > maxDistanceSq) {
        continue;
      }
      const best = matches.get(op.oid);
      if (!best || distanceSq < best.distanceSq) {
        matches.set(op.oid, { m: point.osoitevali_m, distanceSq });
      }
    }
  }
  return matches;
}

// Places each operational point on the single span it is closest to, at the display-m
// of that span's nearest geometry point. Points beyond MAX_OPERATIONAL_POINT_DISTANCE_M
// from every span are dropped. Ties go to the earlier span.
export function placeOperationalPoints(
  tracks: readonly OperationalPointTrack[],
  operationalPoints: readonly ExtOperationalPoint[],
): PlacedOperationalPoint[] {
  const index = indexOperationalPoints(operationalPoints);
  const best = new Map<
    string,
    { trackKey: string; m: number; distanceSq: number }
  >();
  for (const track of tracks) {
    for (const [oid, match] of matchTrackOperationalPoints(track, index)) {
      const current = best.get(oid);
      if (!current || match.distanceSq < current.distanceSq) {
        best.set(oid, { trackKey: track.key, ...match });
      }
    }
  }
  return operationalPoints.flatMap((op) => {
    const placed = best.get(op.toiminnallinen_piste_oid);
    return placed
      ? [
          {
            oid: op.toiminnallinen_piste_oid,
            name: op.nimi,
            trackKey: placed.trackKey,
            m: placed.m,
          },
        ]
      : [];
  });
}
