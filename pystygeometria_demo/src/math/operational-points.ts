import { ExtMeasureAddressPoint, ExtOperationalPoint } from "../api/types";

// An operational point placed onto a single displayed track at the m-value (chainage)
// of the nearest track geometry point.
export interface PlacedOperationalPoint {
  oid: string;
  name: string;
  trackOid: string;
  m: number; // m-value within the track's own chainage
}

// One displayed track's geometry, as needed for placing operational points.
export interface OperationalPointTrack {
  oid: string;
  geometryPoints: readonly ExtMeasureAddressPoint[];
}

// Operational points farther than this from every track point are not shown. The match
// is to the nearest sampled geometry point (not the lines between them), which is plenty
// precise: operational points are constructions far larger than the sampling interval.
export const MAX_OPERATIONAL_POINT_DISTANCE_M = 100;

// Places each operational point on the single track it is closest to, at the chainage of
// that track's nearest geometry point. Points beyond MAX_OPERATIONAL_POINT_DISTANCE_M
// from every track are dropped.
export function placeOperationalPoints(
  tracks: readonly OperationalPointTrack[],
  operationalPoints: readonly ExtOperationalPoint[],
): PlacedOperationalPoint[] {
  const maxDistanceSq =
    MAX_OPERATIONAL_POINT_DISTANCE_M * MAX_OPERATIONAL_POINT_DISTANCE_M;
  return operationalPoints.flatMap((op) => {
    let bestDistanceSq = Infinity;
    let bestTrackOid: string | undefined;
    let bestM = 0;
    for (const track of tracks) {
      for (const point of track.geometryPoints) {
        const dx = point.x - op.sijainti.x;
        const dy = point.y - op.sijainti.y;
        const distanceSq = dx * dx + dy * dy;
        if (distanceSq < bestDistanceSq) {
          bestDistanceSq = distanceSq;
          bestTrackOid = track.oid;
          bestM = point.osoitevali_m;
        }
      }
    }
    if (bestTrackOid === undefined || bestDistanceSq > maxDistanceSq) {
      return [];
    }
    return [
      {
        oid: op.toiminnallinen_piste_oid,
        name: op.nimi,
        trackOid: bestTrackOid,
        m: bestM,
      },
    ];
  });
}
