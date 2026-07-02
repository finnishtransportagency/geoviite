import { describe, expect, test } from "vitest";
import { ExtMeasureAddressPoint, ExtOperationalPoint } from "../api/types";
import {
  OperationalPointTrack,
  placeOperationalPoints,
} from "./operational-points";

function geometryPoint(
  x: number,
  y: number,
  osoitevali_m: number,
): ExtMeasureAddressPoint {
  return { x, y, osoitevali_m, rataosoite: `0000+${osoitevali_m}` };
}

function operationalPoint(
  oid: string,
  name: string,
  x: number,
  y: number,
): ExtOperationalPoint {
  return {
    toiminnallinen_piste_oid: oid,
    nimi: name,
    sijainti: { x, y },
  };
}

// A straight track along the x-axis sampled every 10 m, m == x.
const trackA: OperationalPointTrack = {
  oid: "A",
  geometryPoints: Array.from({ length: 101 }, (_, i) =>
    geometryPoint(i * 10, 0, i * 10),
  ),
};

// A parallel track 200 m north.
const trackB: OperationalPointTrack = {
  oid: "B",
  geometryPoints: Array.from({ length: 101 }, (_, i) =>
    geometryPoint(i * 10, 200, i * 10),
  ),
};

describe("placeOperationalPoints", () => {
  test("places a point at the chainage of the nearest track point", () => {
    const placed = placeOperationalPoints(
      [trackA],
      [operationalPoint("op1", "Station", 503, 20)],
    );
    expect(placed).toEqual([
      { oid: "op1", name: "Station", trackOid: "A", m: 500 },
    ]);
  });

  test("ignores points farther than 100 m from every track point", () => {
    expect(
      placeOperationalPoints(
        [trackA],
        [operationalPoint("far", "Far", 500, 150)],
      ),
    ).toEqual([]);
    // Just within range.
    expect(
      placeOperationalPoints(
        [trackA],
        [operationalPoint("near", "Near", 500, 99)],
      ),
    ).toEqual([{ oid: "near", name: "Near", trackOid: "A", m: 500 }]);
  });

  test("shows a point only on the single closest track", () => {
    const placed = placeOperationalPoints(
      [trackA, trackB],
      // 60 m from A, 140 m from B — only within range of A.
      [operationalPoint("op", "Between", 300, 60)],
    );
    expect(placed).toEqual([
      { oid: "op", name: "Between", trackOid: "A", m: 300 },
    ]);
  });

  test("assigns to the closer track when within range of both", () => {
    const placed = placeOperationalPoints(
      [trackA, trackB],
      // 90 m from A, 110 m from B — in range of both, closer to A.
      [operationalPoint("op", "Closer to A", 300, 90)],
    );
    expect(placed).toEqual([
      { oid: "op", name: "Closer to A", trackOid: "A", m: 300 },
    ]);
  });

  test("drops nothing and finds nearest among many points", () => {
    const placed = placeOperationalPoints(
      [trackA],
      [
        operationalPoint("a", "A", 4, 5),
        operationalPoint("b", "B", 1000, 5),
        operationalPoint("c", "C", 2000, 5),
      ],
    );
    // a is closest to the m=0 sample; c is at x=2000, off the end of the track
    // (max point x=1000), 1000 m away → dropped.
    expect(placed).toEqual([
      { oid: "a", name: "A", trackOid: "A", m: 0 },
      { oid: "b", name: "B", trackOid: "A", m: 1000 },
    ]);
  });
});
