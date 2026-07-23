// Minimal snap logic ported from Geoviite — only snaps to geometry elements
// (PVI intersection points and curve tangent endpoints) whose track addresses
// are known.

import { Coordinates, heightToY, mToX, xToM } from "./coordinates";
import { Layout } from "./layout";
import { DiagramTrack } from "./diagram-model";
import { profileHeightAtM } from "./profile";
import { parseTrackAddress, TrackAddress } from "./track-address";

export type SnapTarget = "pviPoint" | "tangentPoint";

export interface SnappedPoint {
  snapTarget: SnapTarget;
  diagramM: number;
  xPositionPx: number;
  yPositionPx: number;
  height: number;
  address: TrackAddress;
}

interface SnapCandidate {
  diagramM: number;
  height: number;
  address: TrackAddress;
  snapTarget: SnapTarget;
}

function collectSnapCandidates(
  tracks: DiagramTrack[],
  layout: Layout,
  drawTangentArrows: boolean,
): SnapCandidate[] {
  const candidates: SnapCandidate[] = [];

  layout.spans.forEach((span, spanIndex) => {
    const track = tracks[spanIndex];
    if (!track) return;

    // A route span's item list covers its whole track, reaching past the span's
    // m-range; points outside the span are not drawn, so don't snap to them.
    const withinSpan = (m: number) => m >= span.startM && m <= span.endM;

    for (const item of track.items) {
      const baseAddress = parseTrackAddress(item.pointAddress);
      const startAddress = parseTrackAddress(item.startAddress);
      const endAddress = parseTrackAddress(item.endAddress);
      if (!baseAddress) continue;

      // PVI intersection point — show the on-curve (profile) height so the
      // tooltip dot sits on the drawn track profile, not at the intersection
      // diamond.
      if (withinSpan(item.pointM)) {
        const profileHeight = profileHeightAtM(track.items, item.pointM);
        candidates.push({
          diagramM: span.offsetX + (item.pointM - span.startM),
          height: profileHeight ?? item.pointHeight,
          address: baseAddress,
          snapTarget: "pviPoint",
        });
      }

      if (drawTangentArrows) {
        // Curve start tangent point
        if (startAddress && withinSpan(item.startM)) {
          candidates.push({
            diagramM: span.offsetX + (item.startM - span.startM),
            height: item.startHeight,
            address: startAddress,
            snapTarget: "tangentPoint",
          });
        }

        // Curve end tangent point
        if (endAddress && withinSpan(item.endM)) {
          candidates.push({
            diagramM: span.offsetX + (item.endM - span.startM),
            height: item.endHeight,
            address: endAddress,
            snapTarget: "tangentPoint",
          });
        }
      }
    }
  });

  return candidates;
}

export function getSnappedPoint(
  mousePosition: [number, number] | undefined,
  tracks: DiagramTrack[],
  layout: Layout,
  coordinates: Coordinates,
  drawTangentArrows: boolean,
): SnappedPoint | undefined {
  if (!mousePosition) return undefined;

  const [mouseX] = mousePosition;
  const diagramM = xToM(coordinates, mouseX);
  const snapDistanceM = (coordinates.endM - coordinates.startM) * 0.02;

  const candidates = collectSnapCandidates(tracks, layout, drawTangentArrows);

  let closest: SnapCandidate | undefined;
  let closestDistance = Infinity;

  for (const candidate of candidates) {
    if (
      candidate.diagramM < coordinates.startM ||
      candidate.diagramM > coordinates.endM
    ) {
      continue;
    }
    const distance = Math.abs(diagramM - candidate.diagramM);
    if (distance < closestDistance) {
      closest = candidate;
      closestDistance = distance;
    }
  }

  if (!closest || closestDistance > snapDistanceM) {
    return undefined;
  }

  return {
    snapTarget: closest.snapTarget,
    diagramM: closest.diagramM,
    xPositionPx: mToX(coordinates, closest.diagramM),
    yPositionPx: heightToY(coordinates, closest.height),
    height: closest.height,
    address: closest.address,
  };
}
