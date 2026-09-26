import { ExtLocationTrackProfileResponse } from "../api/types";

// One PVI with its vertical curve and the straight sections around it, in a flat,
// diagram-friendly shape. All m-values (stations) are chainage along the location
// track's own geometry.
export interface PviItem {
  id: string;
  startM: number; // curve start tangent point
  pointM: number; // PVI
  endM: number; // curve end tangent point
  startHeight: number;
  startSlope: number;
  startAddress: string;
  pointHeight: number;
  pointAddress: string;
  endHeight: number;
  endSlope: number;
  endAddress: string;
  radius: number;
  tangent: number;
  backwardLength: number; // distance from previous PVI (or track start) to this PVI
  backwardStraightLength: number; // straight portion of that
  forwardLength: number; // distance from this PVI to the next PVI (or track end)
  forwardStraightLength: number;
}

export function pviItemsFromProfile(
  response: ExtLocationTrackProfileResponse,
): PviItem[] {
  return response.osoitevali.taitepisteet
    .flatMap((pvi, index): PviItem[] => {
      const { alku, taite, loppu } = pvi.paaluluku;
      // Station values are null for points beyond the track's ends; such a PVI has no
      // place on the m-axis, so it is left out of the diagram.
      if (
        typeof alku !== "number" ||
        typeof taite !== "number" ||
        typeof loppu !== "number"
      ) {
        return [];
      }
      return [
        {
          id: `${response.sijaintiraide_oid}-${index}`,
          startM: alku,
          pointM: taite,
          endM: loppu,
          startHeight: pvi.pyoristyksen_alku.korkeus_n2000,
          startSlope: pvi.pyoristyksen_alku.kaltevuus,
          startAddress: pvi.pyoristyksen_alku.sijainti.rataosoite,
          pointHeight: pvi.taite.korkeus_n2000,
          pointAddress: pvi.taite.sijainti.rataosoite,
          endHeight: pvi.pyoristyksen_loppu.korkeus_n2000,
          endSlope: pvi.pyoristyksen_loppu.kaltevuus,
          endAddress: pvi.pyoristyksen_loppu.sijainti.rataosoite,
          radius: pvi.pyoristyssade,
          tangent: pvi.tangentti,
          backwardLength: pvi.kaltevuusjakso_taaksepain.pituus,
          backwardStraightLength: pvi.kaltevuusjakso_taaksepain.suora_osa,
          forwardLength: pvi.kaltevuusjakso_eteenpain.pituus,
          forwardStraightLength: pvi.kaltevuusjakso_eteenpain.suora_osa,
        },
      ];
    })
    .sort((a, b) => a.pointM - b.pointM);
}

// Maximum absolute slope difference that is still considered "matching" between
// adjacent PviItems. Railway grades are shallow (typically under 25 ‰), so a
// plain absolute comparison of the dimensionless slope values works well. 0.1 ‰
// accommodates survey-data rounding while still catching genuine mismatches.
export const SLOPE_TOLERANCE = 0.0001;

// A PviItem belonging to a nearby location track, carried into the current track's
// m-frame so its grade line can continue across track boundaries. Location tracks
// along the same track number are physically continuous, so a PVI on a nearby track
// still defines the height of a (often short) track that has no PVIs of its own.
//
// `localM` places the item's near tangent point in the current track's own m-frame:
// for a preceding item it is the local station of the item's end tangent (≤ 0), for a
// following item the local station of its start tangent (≥ the track's length).
export interface NeighborPvi {
  item: PviItem;
  localM: number;
}

// Index of the first item whose curve ends at or after m (lower bound on endM). The
// items are sorted by station and don't overlap, so endM is ascending too.
function firstItemEndingAtOrAfter(items: PviItem[], m: number): number {
  let lo = 0;
  let hi = items.length;
  while (lo < hi) {
    const mid = (lo + hi) >>> 1;
    if (items[mid]!.endM < m) {
      lo = mid + 1;
    } else {
      hi = mid;
    }
  }
  return lo;
}

// Height on the straight grade line leading into `item`'s curve. If the track's own
// previous item bounds that straight on the low side and its endSlope disagrees with
// this item's startSlope, the straight is ill-defined (the items come from different
// plans): the interior of the gap is undefined, but the height stays defined at the
// boundary stations themselves.
function straightHeightBeforeItem(
  item: PviItem,
  ownPrev: PviItem | undefined,
  m: number,
): number | undefined {
  if (
    ownPrev &&
    m > ownPrev.endM &&
    m < item.startM &&
    Math.abs(ownPrev.endSlope - item.startSlope) > SLOPE_TOLERANCE
  ) {
    return undefined;
  }
  return item.startHeight + item.startSlope * (m - item.startM);
}

// Height within `item`'s vertical curve: parabolic approximation of the circular arc.
// Radius 0 marks an angle point with no curve, leaving just the straight grade line.
function curveHeightWithinItem(item: PviItem, m: number): number {
  const d = m - item.startM;
  const curveTerm = item.radius === 0 ? 0 : (d * d) / (2 * item.radius);
  return item.startHeight + item.startSlope * d + curveTerm;
}

// Height on a track with no PviItems of its own, borrowed from the neighbouring
// tracks' grade lines: extrapolated forward from `prev`, or backward from `next` when
// there is nothing before. When both exist their slopes must agree, or the borrowed
// straight is ill-defined (the neighbours come from different plans) and the whole
// track stays undefined.
function borrowedHeightAtM(
  m: number,
  prev?: NeighborPvi,
  next?: NeighborPvi,
): number | undefined {
  if (
    prev &&
    next &&
    Math.abs(prev.item.endSlope - next.item.startSlope) > SLOPE_TOLERANCE
  ) {
    return undefined;
  }
  if (prev) {
    return prev.item.endHeight + prev.item.endSlope * (m - prev.localM);
  }
  if (next) {
    return next.item.startHeight + next.item.startSlope * (m - next.localM);
  }
  return undefined;
}

// N2000 height of the actual track profile at a given m-value: straight grade lines
// between curves, parabolic approximation of the circular arcs within them (accurate to
// sub-millimeter for railway grades).
//
// A track with PviItems of its own trusts them alone: `prev` and `next` are ignored,
// and outside the covered span the height extrapolates with the first item's start
// slope and the last item's end slope. Returns undefined when m falls strictly inside
// a gap between two own PviItems whose slopes disagree — the straight section is
// ill-defined there because the items come from different plans. The height *is* still
// defined at each item's own startM and endM.
//
// A track with no PviItems of its own borrows the grade line from the nearest PVIs on
// the neighbouring tracks (see NeighborPvi and borrowedHeightAtM). With no own items
// and no neighbours the height is undefined.
export function profileHeightAtM(
  items: PviItem[],
  m: number,
  prev?: NeighborPvi,
  next?: NeighborPvi,
): number | undefined {
  if (items.length === 0) {
    return borrowedHeightAtM(m, prev, next);
  }
  const index = firstItemEndingAtOrAfter(items, m);
  const item = items[index];
  if (!item) {
    // m is past every item: extrapolate forward with the last item's end slope.
    const last = items[items.length - 1]!;
    return last.endHeight + last.endSlope * (m - last.endM);
  }
  if (m <= item.startM) {
    return straightHeightBeforeItem(item, items[index - 1], m);
  }
  // endM >= m is guaranteed by the search, so m lies within this item's curve.
  return curveHeightWithinItem(item, m);
}
