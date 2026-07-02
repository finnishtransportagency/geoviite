import {
  ExtMeasureAddressPoint,
  ExtLocationTrack,
  LocationTrackResponse,
} from "../api/types";
import { Layout, TrackSpanInput, ViewRange } from "./layout";
import {
  NeighborPvi,
  profileHeightAtM,
  PviItem,
  pviItemsFromProfile,
} from "./profile";

// Everything the diagram needs about one displayed location track.
export interface DiagramTrack extends TrackSpanInput {
  items: PviItem[];
  // Map geometry points (x/y with chainage m), used to place operational points along
  // the track.
  geometryPoints: ExtMeasureAddressPoint[];
  // Nearest PVIs carried from the neighbouring displayed tracks, so a track with no
  // PVIs of its own (a short track may have none) still gets a height from the
  // continuous grade line running through the track number. Only consulted when the
  // track has no own PVIs. Undefined when no displayed track on that side has a PVI
  // to carry.
  prevItem?: NeighborPvi;
  nextItem?: NeighborPvi;
}

// Builds the displayed tracks from the selected location tracks (already sorted by start
// address) and their fetched profile+geometry responses. Tracks whose response has not
// arrived yet are left out; they pop in when loaded. The span is [0, track length], the
// length taken from the m-value of the track's final geometry point (the backend sends
// the track's actual end point last). Falls back to the PVI extent if the geometry has
// no points; the span is also widened to cover any PVI curve reaching past the end point.
export function buildDiagramTracks(
  selectedTracks: ExtLocationTrack[],
  responsesByOid: Record<string, LocationTrackResponse | undefined>,
): DiagramTrack[] {
  const tracks = selectedTracks.flatMap((track): DiagramTrack[] => {
    const response = responsesByOid[track.sijaintiraide_oid];
    if (!response) {
      return [];
    }
    const items = pviItemsFromProfile(response.profile);
    const geometryPoints = response.geometry.osoitevali.pisteet;
    const lastItem = items[items.length - 1];
    const lengthM =
      geometryPoints[geometryPoints.length - 1]?.osoitevali_m ?? 0;
    return [
      {
        oid: track.sijaintiraide_oid,
        name: track.sijaintiraidetunnus,
        startM: 0,
        endM: Math.max(lengthM, lastItem?.endM ?? 0),
        items,
        geometryPoints,
      },
    ];
  });
  linkNeighbouringPvis(tracks);
  return tracks;
}

// Fills in each track's prevItem/nextItem from the displayed tracks around it. The tracks
// are physically continuous (each one's end meets the next one's start), so a PVI is
// carried across boundaries — and across whole tracks that have no PVIs of their own —
// with its position translated into each receiving track's own m-frame along the way.
function linkNeighbouringPvis(tracks: DiagramTrack[]): void {
  // Forward pass: `gap` accumulates the distance from the carried PVI's end tangent to
  // the current track's start, so the tangent sits at local m = -gap.
  let prev: { item: PviItem; gap: number } | undefined;
  for (const track of tracks) {
    track.prevItem = prev && { item: prev.item, localM: -prev.gap };
    const length = track.endM - track.startM;
    const last = track.items[track.items.length - 1];
    if (last) {
      prev = { item: last, gap: length - last.endM };
    } else if (prev) {
      prev = { item: prev.item, gap: prev.gap + length };
    }
  }

  // Backward pass: `gap` accumulates the distance from the current track's end forward
  // to the carried PVI's start tangent, so the tangent sits at local m = length + gap.
  let next: { item: PviItem; gap: number } | undefined;
  for (let i = tracks.length - 1; i >= 0; i--) {
    const track = tracks[i]!;
    const length = track.endM - track.startM;
    track.nextItem = next && { item: next.item, localM: length + next.gap };
    const first = track.items[0];
    if (first) {
      next = { item: first, gap: first.startM };
    } else if (next) {
      next = { item: next.item, gap: next.gap + length };
    }
  }
}

// Height samples relevant to the visible view: heights of PVI items intersecting the
// view plus the profile heights at the visible edges of each track (so long straights
// crossing the whole view still set the bounds).
export function collectVisibleHeights(
  tracks: DiagramTrack[],
  layout: Layout,
  view: ViewRange,
): number[] {
  const heights: number[] = [];
  layout.spans.forEach((span, spanIndex) => {
    const track = tracks[spanIndex];
    if (!track) {
      return;
    }
    const visibleStart = Math.max(view.startX, span.offsetX);
    const visibleEnd = Math.min(view.endX, span.offsetX + span.lengthM);
    if (visibleStart >= visibleEnd) {
      return;
    }
    const localStart = span.startM + (visibleStart - span.offsetX);
    const localEnd = span.startM + (visibleEnd - span.offsetX);
    for (const item of track.items) {
      if (item.endM >= localStart && item.startM <= localEnd) {
        heights.push(item.startHeight, item.pointHeight, item.endHeight);
      }
    }
    // Edge heights also cover tracks whose own PVIs don't reach the visible edge — and
    // tracks with no PVIs at all — by borrowing the grade line from the neighbours.
    const edgeStart = profileHeightAtM(
      track.items,
      localStart,
      track.prevItem,
      track.nextItem,
    );
    const edgeEnd = profileHeightAtM(
      track.items,
      localEnd,
      track.prevItem,
      track.nextItem,
    );
    if (edgeStart !== undefined) {
      heights.push(edgeStart);
    }
    if (edgeEnd !== undefined) {
      heights.push(edgeEnd);
    }
  });
  return heights;
}
