// Turning a routing API response into displayed track spans. A route section covers a
// piece of one location track — unlike the whole-track display, the piece has its own
// m-range within the track, and the route can run *against* the track's chainage
// ("laskeva"), in which case the piece is drawn mirrored: track m-values decrease left
// to right in the diagram.
//
// Rather than teach every rendering component about mirrored m-axes, each span gets its
// own *display frame*: display-m runs from 0 at the span's left (route-start) edge to
// its length at the right edge, and the span's PVI items and geometry points are
// translated (and for reversed spans, reflected) into that frame up front. Downstream —
// layout, height evaluation, rendering, snapping — everything works in the display
// frame exactly as it does for whole tracks.

import {
  ExtMeasureAddressPoint,
  ExtRoute,
  ExtRouteSection,
} from "../api/types";
import { PviItem } from "./profile";

export interface RouteSpan {
  // Unique among the route's spans; the same track can appear in several sections
  // (routes zigzag between parallel tracks over switch areas), so the track oid alone
  // does not identify a span.
  key: string;
  trackOid: string;
  minM: number; // the section's m-range within the track's own chainage
  maxM: number;
  reversed: boolean; // route runs against the track's chainage
}

export function routeSpans(route: ExtRoute): RouteSpan[] {
  return route.reitin_osat.map(
    (section: ExtRouteSection, index: number): RouteSpan => ({
      key: `${index}:${section.sijaintiraide_oid}`,
      trackOid: section.sijaintiraide_oid,
      minM: Math.min(section.alku.m_arvo, section.loppu.m_arvo),
      maxM: Math.max(section.alku.m_arvo, section.loppu.m_arvo),
      reversed: section.suunta === "laskeva",
    }),
  );
}

export function toDisplayM(span: RouteSpan, m: number): number {
  return span.reversed ? span.maxM - m : m - span.minM;
}

// The whole track's PVI items translated into the span's display frame. Items outside
// the span's m-range are kept: they still define the profile height within the span
// (a short span often has no PVI of its own inside it, its heights coming from the
// grade line between PVIs on either side).
//
// A reversed span reflects each item: start and end swap, slopes negate, and the radius
// stays as is — the parabolic curve height used by profileHeightAtM is symmetric under
// this reflection, so heights at reflected m-values match exactly.
export function pviItemsToDisplayFrame(
  items: readonly PviItem[],
  span: RouteSpan,
): PviItem[] {
  if (!span.reversed) {
    return items.map((item) => ({
      ...item,
      startM: item.startM - span.minM,
      pointM: item.pointM - span.minM,
      endM: item.endM - span.minM,
    }));
  }
  return items
    .map(
      (item): PviItem => ({
        ...item,
        startM: span.maxM - item.endM,
        pointM: span.maxM - item.pointM,
        endM: span.maxM - item.startM,
        startHeight: item.endHeight,
        startSlope: -item.endSlope,
        startAddress: item.endAddress,
        endHeight: item.startHeight,
        endSlope: -item.startSlope,
        endAddress: item.startAddress,
        backwardLength: item.forwardLength,
        backwardStraightLength: item.forwardStraightLength,
        forwardLength: item.backwardLength,
        forwardStraightLength: item.backwardStraightLength,
      }),
    )
    .reverse(); // keep the items sorted by ascending (display) pointM
}

// The track's geometry points restricted to the span's m-range and translated into the
// display frame; used for placing operational points along the span.
export function geometryPointsToDisplayFrame(
  points: readonly ExtMeasureAddressPoint[],
  span: RouteSpan,
): ExtMeasureAddressPoint[] {
  const within = points
    .filter(
      (point) =>
        point.osoitevali_m >= span.minM && point.osoitevali_m <= span.maxM,
    )
    .map((point) => ({
      ...point,
      osoitevali_m: toDisplayM(span, point.osoitevali_m),
    }));
  return span.reversed ? within.reverse() : within;
}
