import { describe, expect, test } from "vitest";
import { ExtRoute, ExtRouteSectionEndpoint } from "../api/types";
import { PviItem, profileHeightAtM } from "./profile";
import {
  geometryPointsToDisplayFrame,
  pviItemsToDisplayFrame,
  routeSpans,
  toDisplayM,
} from "./route";

const endpoint = (m: number): ExtRouteSectionEndpoint => ({
  tyyppi: "sijainti_raiteella",
  x: 0,
  y: 0,
  m_arvo: m,
});

const route: ExtRoute = {
  pituus: 800,
  reitin_osat: [
    {
      sijaintiraide_oid: "track-1",
      ratanumero_oid: "tn-1",
      alku: endpoint(100),
      loppu: endpoint(600),
      suunta: "nouseva",
      pituus: 500,
    },
    {
      sijaintiraide_oid: "track-2",
      ratanumero_oid: "tn-1",
      alku: endpoint(300),
      loppu: endpoint(0),
      suunta: "laskeva",
      pituus: 300,
    },
  ],
};

// A PVI whose curve runs from m 200 to 300 with a sag radius, sitting inside a track
// that also has a second PVI further along (heights consistent with the slopes).
const item = (overrides: Partial<PviItem>): PviItem => ({
  id: "item",
  startM: 200,
  pointM: 250,
  endM: 300,
  startHeight: 10,
  startSlope: -0.01,
  startAddress: "0001+0200",
  pointHeight: 9.5,
  pointAddress: "0001+0250",
  endHeight: 9.75,
  endSlope: 0.01,
  endAddress: "0001+0300",
  radius: 5000,
  tangent: 50,
  backwardLength: 200,
  backwardStraightLength: 150,
  forwardLength: 100,
  forwardStraightLength: 50,
  ...overrides,
});

describe("routeSpans", () => {
  test("builds a span per section with ordered m-range, direction and unique keys", () => {
    const spans = routeSpans(route);
    expect(spans).toEqual([
      {
        key: "0:track-1",
        trackOid: "track-1",
        minM: 100,
        maxM: 600,
        reversed: false,
      },
      {
        key: "1:track-2",
        trackOid: "track-2",
        minM: 0,
        maxM: 300,
        reversed: true,
      },
    ]);
  });
});

describe("pviItemsToDisplayFrame", () => {
  const forwardSpan = routeSpans(route)[0]!;
  const reversedSpan = routeSpans(route)[1]!;

  test("a forward span only shifts the m-values", () => {
    const [shifted] = pviItemsToDisplayFrame([item({})], forwardSpan);
    expect(shifted).toEqual(
      item({ startM: 100, pointM: 150, endM: 200 }),
    );
  });

  test("a reversed span reflects the item, swapping ends and negating slopes", () => {
    const [mirrored] = pviItemsToDisplayFrame([item({})], reversedSpan);
    expect(mirrored).toEqual(
      item({
        startM: 0,
        pointM: 50,
        endM: 100,
        startHeight: 9.75,
        startSlope: -0.01,
        startAddress: "0001+0300",
        endHeight: 10,
        endSlope: 0.01,
        endAddress: "0001+0200",
        backwardLength: 100,
        backwardStraightLength: 50,
        forwardLength: 200,
        forwardStraightLength: 150,
      }),
    );
  });

  test("reversal keeps the items sorted by display-m", () => {
    const items = [
      item({ id: "a" }),
      item({ id: "b", startM: 400, pointM: 450, endM: 500 }),
    ];
    const mirrored = pviItemsToDisplayFrame(items, reversedSpan);
    expect(mirrored.map((i) => i.id)).toEqual(["b", "a"]);
    expect(mirrored[0]!.pointM).toBeLessThan(mirrored[1]!.pointM);
  });

  test("profile heights at reflected m-values match the original exactly", () => {
    // Two physically consistent PVIs (each curve's end height and slope follow from
    // its start and radius, and PVI b's start sits on the grade line from a's end), so
    // every m in the covered range has a defined height: curves, the straight between
    // them, and extrapolated ends.
    const items = [
      item({ id: "a", endHeight: 10 }),
      item({
        id: "b",
        startM: 400,
        pointM: 450,
        endM: 500,
        startHeight: 11,
        startSlope: 0.01,
        pointHeight: 11.5,
        endHeight: 11,
        endSlope: -0.01,
        radius: -5000,
      }),
    ];
    const span = {
      key: "s",
      trackOid: "track",
      minM: 150,
      maxM: 550,
      reversed: true,
    };
    const mirrored = pviItemsToDisplayFrame(items, span);
    for (let m = 150; m <= 550; m += 7) {
      expect(profileHeightAtM(mirrored, toDisplayM(span, m))).toBeCloseTo(
        profileHeightAtM(items, m)!,
        9,
      );
    }
  });
});

describe("geometryPointsToDisplayFrame", () => {
  const point = (m: number) => ({
    x: m,
    y: 0,
    osoitevali_m: m,
    rataosoite: `addr-${m}`,
  });

  test("restricts to the span's range and maps into the display frame", () => {
    const span = routeSpans(route)[1]!; // reversed, m-range 0..300
    const points = geometryPointsToDisplayFrame(
      [point(0), point(150), point(299), point(310)],
      span,
    );
    expect(points.map((p) => p.osoitevali_m)).toEqual([1, 150, 300]);
    expect(points.map((p) => p.rataosoite)).toEqual([
      "addr-299",
      "addr-150",
      "addr-0",
    ]);
  });
});
