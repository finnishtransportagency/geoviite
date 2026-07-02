import { describe, expect, test } from "vitest";
import {
  clampView,
  computeLayout,
  pannedView,
  remapViewKeepingCenter,
  trackPositionToX,
  xToTrackPosition,
  zoomedView,
} from "./layout";

const trackA = { oid: "A", name: "A", startM: 0, endM: 500 };
const trackB = { oid: "B", name: "B", startM: 100, endM: 400 };
const trackC = { oid: "C", name: "C", startM: 0, endM: 1000 };

describe("computeLayout", () => {
  test("concatenates spans", () => {
    const layout = computeLayout([trackA, trackB, trackC]);
    expect(layout.totalLength).toBe(1800);
    expect(layout.spans.map((s) => s.offsetX)).toEqual([0, 500, 800]);
    expect(layout.spans.map((s) => s.lengthM)).toEqual([500, 300, 1000]);
  });
});

describe("xToTrackPosition / trackPositionToX", () => {
  const layout = computeLayout([trackA, trackB]);

  test("maps diagram-x into track chainage and back", () => {
    expect(xToTrackPosition(layout, 600)).toEqual({ oid: "B", m: 200 });
    expect(trackPositionToX(layout, { oid: "B", m: 200 })).toBe(600);
  });

  test("respects the track-local start m-value", () => {
    expect(xToTrackPosition(layout, 500)).toEqual({ oid: "A", m: 500 });
    expect(trackPositionToX(layout, { oid: "B", m: 100 })).toBe(500);
  });

  test("is undefined outside the layout or the track span", () => {
    expect(xToTrackPosition(layout, 900)).toBeUndefined();
    expect(trackPositionToX(layout, { oid: "B", m: 50 })).toBeUndefined();
    expect(trackPositionToX(layout, { oid: "missing", m: 50 })).toBeUndefined();
  });
});

describe("remapViewKeepingCenter", () => {
  test("keeps the center at the same m-value of the same track when a track is added before it", () => {
    const oldLayout = computeLayout([trackB]); // B alone: x 0..300
    const newLayout = computeLayout([trackA, trackB]); // B now at x 500..800
    // center at x=150 = B m=250
    const view = remapViewKeepingCenter(
      { startX: 100, endX: 200 },
      oldLayout,
      newLayout,
    );
    expect((view.startX + view.endX) / 2).toBeCloseTo(650, 6);
    expect(view.endX - view.startX).toBeCloseTo(100, 6);
  });

  test("keeps the diagram-x when the centered track was removed", () => {
    const oldLayout = computeLayout([trackA, trackB]);
    const newLayout = computeLayout([trackA]);
    // center at x=250 inside A; removing B keeps it
    const view = remapViewKeepingCenter(
      { startX: 200, endX: 300 },
      oldLayout,
      newLayout,
    );
    expect((view.startX + view.endX) / 2).toBeCloseTo(250, 6);
  });

  test("clamps into the new extent", () => {
    const oldLayout = computeLayout([trackA, trackB]);
    const newLayout = computeLayout([trackB]);
    // centered far into former A+B territory, beyond B-only total of 300
    const view = remapViewKeepingCenter(
      { startX: 700, endX: 800 },
      oldLayout,
      newLayout,
    );
    expect(view.startX).toBeGreaterThanOrEqual(0);
    expect(view.endX).toBeLessThanOrEqual(300);
    expect(view.endX - view.startX).toBeCloseTo(100, 6);
  });
});

describe("clampView", () => {
  test("shrinks a too-wide view to the total length", () => {
    expect(clampView({ startX: -100, endX: 500 }, 300)).toEqual({
      startX: 0,
      endX: 300,
    });
  });

  test("shifts an out-of-bounds view inside", () => {
    expect(clampView({ startX: -50, endX: 50 }, 300)).toEqual({
      startX: 0,
      endX: 100,
    });
    expect(clampView({ startX: 250, endX: 350 }, 300)).toEqual({
      startX: 200,
      endX: 300,
    });
  });
});

describe("zoomedView", () => {
  test("zooming in keeps the focus point fixed", () => {
    const view = zoomedView({ startX: 0, endX: 1000 }, 1000, 250, -100);
    const factor = Math.pow(1.05, -1);
    expect(view.startX).toBeCloseTo(250 - 250 * factor, 6);
    expect(view.endX).toBeCloseTo(250 + 750 * factor, 6);
  });

  test("zooming out is clamped to the extent", () => {
    const view = zoomedView({ startX: 100, endX: 900 }, 1000, 500, 10000);
    expect(view.startX).toBe(0);
    expect(view.endX).toBe(1000);
  });
});

describe("pannedView", () => {
  test("pans within bounds and clamps at the edges", () => {
    expect(pannedView({ startX: 100, endX: 200 }, 1000, 50)).toEqual({
      startX: 150,
      endX: 250,
    });
    expect(pannedView({ startX: 100, endX: 200 }, 1000, -500)).toEqual({
      startX: 0,
      endX: 100,
    });
    expect(pannedView({ startX: 800, endX: 900 }, 1000, 500)).toEqual({
      startX: 900,
      endX: 1000,
    });
  });
});
