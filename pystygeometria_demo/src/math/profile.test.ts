import { describe, expect, test } from "vitest";
import {
  ExtLocationTrackProfileResponse,
  ExtProfilePviPoint,
} from "../api/types";
import {
  NeighborPvi,
  PviItem,
  SLOPE_TOLERANCE,
  profileHeightAtM,
  pviItemsFromProfile,
} from "./profile";

// The example response from vertical_geometry_api_doc.md, with fields irrelevant to the
// math abbreviated.
const pvi = (
  p: Partial<ExtProfilePviPoint> & Pick<ExtProfilePviPoint, "paaluluku">,
): ExtProfilePviPoint => ({
  pyoristyksen_alku: {
    korkeus_alkuperainen: 0,
    korkeus_n2000: 0,
    kaltevuus: 0,
    sijainti: { x: 0, y: 0, rataosoite: "0000+0000" },
  },
  taite: {
    korkeus_alkuperainen: 0,
    korkeus_n2000: 0,
    sijainti: { x: 0, y: 0, rataosoite: "0000+0000" },
  },
  pyoristyksen_loppu: {
    korkeus_alkuperainen: 0,
    korkeus_n2000: 0,
    kaltevuus: 0,
    sijainti: { x: 0, y: 0, rataosoite: "0000+0000" },
  },
  pyoristyssade: 0,
  tangentti: 0,
  kaltevuusjakso_taaksepain: { pituus: 0, suora_osa: 0 },
  kaltevuusjakso_eteenpain: { pituus: 0, suora_osa: 0 },
  huomiot: [],
  ...p,
});

const exampleResponse: ExtLocationTrackProfileResponse = {
  rataverkon_versio: "204d8b6a-d119-4aca-a400-c53aef947383",
  sijaintiraide_oid: "1.2.246.578.3.10002.1008558",
  koordinaatisto: "EPSG:3067",
  osoitevali: {
    alku: "0623+0931.105",
    loppu: "0624+0692.203",
    taitepisteet: [
      pvi({
        pyoristyksen_alku: {
          korkeus_alkuperainen: 80.676,
          korkeus_n2000: 80.676,
          kaltevuus: -0.00705,
          sijainti: { x: 0, y: 0, rataosoite: "0623+0980.489" },
        },
        taite: {
          korkeus_alkuperainen: 80.574,
          korkeus_n2000: 80.574,
          sijainti: { x: 0, y: 0, rataosoite: "0623+0994.899" },
        },
        pyoristyksen_loppu: {
          korkeus_alkuperainen: 80.567,
          korkeus_n2000: 80.567,
          kaltevuus: -0.0005,
          sijainti: { x: 0, y: 0, rataosoite: "0624+0009.049" },
        },
        pyoristyssade: 4400,
        tangentti: 14.41,
        kaltevuusjakso_taaksepain: { pituus: 63.859, suora_osa: 49.449 },
        kaltevuusjakso_eteenpain: { pituus: 254.737, suora_osa: 220.396 },
        paaluluku: { alku: 49.449, taite: 63.859, loppu: 78.268 },
      }),
      pvi({
        pyoristyksen_alku: {
          korkeus_alkuperainen: 80.457,
          korkeus_n2000: 80.457,
          kaltevuus: -0.0005,
          sijainti: { x: 0, y: 0, rataosoite: "0624+0230.700" },
        },
        taite: {
          korkeus_alkuperainen: 80.447,
          korkeus_n2000: 80.447,
          sijainti: { x: 0, y: 0, rataosoite: "0624+0250.631" },
        },
        pyoristyksen_loppu: {
          korkeus_alkuperainen: 80.477,
          korkeus_n2000: 80.477,
          kaltevuus: 0.001493,
          sijainti: { x: 0, y: 0, rataosoite: "0624+0270.562" },
        },
        pyoristyssade: 20000,
        tangentti: 19.931,
        kaltevuusjakso_taaksepain: { pituus: 254.737, suora_osa: 220.396 },
        kaltevuusjakso_eteenpain: { pituus: 419.321, suora_osa: 379.025 },
        paaluluku: { alku: 298.665, taite: 318.596, loppu: 338.527 },
      }),
      pvi({
        pyoristyksen_alku: {
          korkeus_alkuperainen: 81.042,
          korkeus_n2000: 81.042,
          kaltevuus: 0.001493,
          sijainti: { x: 0, y: 0, rataosoite: "0624+0651.391" },
        },
        taite: {
          korkeus_alkuperainen: 81.073,
          korkeus_n2000: 81.073,
          sijainti: { x: 0, y: 0, rataosoite: "0624+0671.631" },
        },
        pyoristyksen_loppu: {
          korkeus_alkuperainen: 81.124,
          korkeus_n2000: 81.124,
          kaltevuus: 0.002511,
          sijainti: { x: 0, y: 0, rataosoite: "0624+0691.959" },
        },
        pyoristyssade: 40000,
        tangentti: 20.365,
        kaltevuusjakso_taaksepain: { pituus: 419.321, suora_osa: 379.025 },
        kaltevuusjakso_eteenpain: { pituus: 20.608, suora_osa: 0.243 },
        paaluluku: { alku: 717.552, taite: 737.916, loppu: 758.281 },
      }),
    ],
  },
};

const items = pviItemsFromProfile(exampleResponse);

describe("pviItemsFromProfile", () => {
  test("maps and sorts the PVIs", () => {
    expect(items.map((item) => item.pointM)).toEqual([
      63.859, 318.596, 737.916,
    ]);
    expect(items[0]?.startSlope).toBeCloseTo(-0.00705, 6);
    expect(items[0]?.pointAddress).toBe("0623+0994.899");
    expect(items[2]?.radius).toBe(40000);
  });

  test("skips PVIs with null station values", () => {
    // Seen in live data (e.g. location track oid 1.2.246.578.3.10002.191671): a PVI
    // whose intersection point and curve end lie beyond the track's end has
    // taite/loppu null. Such a PVI cannot be placed on the m-axis and must not be
    // sorted as if its null m-values were 0.
    const response: ExtLocationTrackProfileResponse = {
      ...exampleResponse,
      osoitevali: {
        ...exampleResponse.osoitevali,
        taitepisteet: [
          ...exampleResponse.osoitevali.taitepisteet,
          pvi({ paaluluku: { alku: 800.123, taite: null, loppu: null } }),
        ],
      },
    };
    expect(pviItemsFromProfile(response).map((item) => item.pointM)).toEqual([
      63.859, 318.596, 737.916,
    ]);
  });
});

describe("profileHeightAtM", () => {
  test("worked example 1: straight segment at m=200", () => {
    expect(profileHeightAtM(items, 200)).toBeCloseTo(80.506, 3);
  });

  test("worked example 2: curved segment at m=55", () => {
    // the doc's worked example rounds its intermediate terms to millimeters, so it
    // lands on 80.641 while the unrounded parabola gives 80.6404
    expect(profileHeightAtM(items, 55)).toBeCloseTo(80.641, 2);
  });

  test("matches the tangent point heights at curve boundaries", () => {
    for (const item of items) {
      expect(profileHeightAtM(items, item.startM)).toBeCloseTo(
        item.startHeight,
        3,
      );
      expect(profileHeightAtM(items, item.endM)).toBeCloseTo(item.endHeight, 2);
    }
  });

  test("extrapolates before the first curve with the first slope", () => {
    const first = items[0];
    expect(first).toBeDefined();
    if (!first) return;
    expect(profileHeightAtM(items, 0)).toBeCloseTo(
      first.startHeight + first.startSlope * (0 - first.startM),
      6,
    );
  });

  test("extrapolates after the last curve with the last slope", () => {
    const last = items[items.length - 1];
    expect(last).toBeDefined();
    if (!last) return;
    expect(profileHeightAtM(items, 800)).toBeCloseTo(
      last.endHeight + last.endSlope * (800 - last.endM),
      6,
    );
  });

  test("is undefined without PVIs", () => {
    expect(profileHeightAtM([], 100)).toBeUndefined();
  });
});

// Helper to create a minimal PviItem for slope-mismatch tests.
const makePviItem = (overrides: Partial<PviItem>): PviItem => ({
  id: "test",
  startM: 0,
  pointM: 50,
  endM: 100,
  startHeight: 100,
  startSlope: 0,
  startAddress: "0000+0000",
  pointHeight: 100,
  pointAddress: "0000+0000",
  endHeight: 100,
  endSlope: 0,
  endAddress: "0000+0000",
  radius: 0,
  tangent: 0,
  backwardLength: 0,
  backwardStraightLength: 0,
  forwardLength: 0,
  forwardStraightLength: 0,
  ...overrides,
});

describe("profileHeightAtM slope-mismatch gap", () => {
  const mismatchedItems: PviItem[] = [
    makePviItem({
      id: "a",
      startM: 0,
      pointM: 50,
      endM: 100,
      startHeight: 100,
      endHeight: 100.5,
      endSlope: 0.005,
    }),
    makePviItem({
      id: "b",
      startM: 200,
      pointM: 250,
      endM: 300,
      startHeight: 99,
      endHeight: 99,
      startSlope: -0.005,
    }),
  ];

  test("is undefined in the interior of a slope-mismatched gap", () => {
    expect(profileHeightAtM(mismatchedItems, 150)).toBeUndefined();
  });

  test("is undefined just past the previous item's endM", () => {
    expect(profileHeightAtM(mismatchedItems, 100.001)).toBeUndefined();
  });

  test("is undefined just before the next item's startM", () => {
    expect(profileHeightAtM(mismatchedItems, 199.999)).toBeUndefined();
  });

  test("is still defined at the previous item's endM", () => {
    // endM=100 is within item "a"'s own curve, so the curve formula applies.
    // With startSlope=0, radius=0, startHeight=100 → height stays 100.
    expect(profileHeightAtM(mismatchedItems, 100)).toBeCloseTo(100, 3);
  });

  test("is still defined at the next item's startM", () => {
    expect(profileHeightAtM(mismatchedItems, 200)).toBeCloseTo(99, 3);
  });

  test("is defined when slopes match exactly", () => {
    const matchedItems: PviItem[] = [
      makePviItem({
        id: "a",
        startM: 0,
        pointM: 50,
        endM: 100,
        endHeight: 100.5,
        endSlope: 0.005,
      }),
      makePviItem({
        id: "b",
        startM: 200,
        pointM: 250,
        endM: 300,
        startHeight: 101,
        startSlope: 0.005,
      }),
    ];
    expect(profileHeightAtM(matchedItems, 150)).toBeDefined();
  });

  test("is defined when slope difference is within tolerance", () => {
    const nearlyMatchedItems: PviItem[] = [
      makePviItem({
        id: "a",
        startM: 0,
        pointM: 50,
        endM: 100,
        endHeight: 100.5,
        endSlope: 0.005,
      }),
      makePviItem({
        id: "b",
        startM: 200,
        pointM: 250,
        endM: 300,
        startHeight: 101,
        startSlope: 0.005 + SLOPE_TOLERANCE * 0.99,
      }),
    ];
    expect(profileHeightAtM(nearlyMatchedItems, 150)).toBeDefined();
  });

  test("is undefined when slope difference just exceeds tolerance", () => {
    const barelyMismatchedItems: PviItem[] = [
      makePviItem({
        id: "a",
        startM: 0,
        pointM: 50,
        endM: 100,
        endHeight: 100.5,
        endSlope: 0.005,
      }),
      makePviItem({
        id: "b",
        startM: 200,
        pointM: 250,
        endM: 300,
        startHeight: 101,
        startSlope: 0.005 + SLOPE_TOLERANCE * 1.5,
      }),
    ];
    expect(profileHeightAtM(barelyMismatchedItems, 150)).toBeUndefined();
  });

  test("extrapolation before the first item is unaffected by mismatch", () => {
    // The gap check only applies between items, not before the first one.
    expect(profileHeightAtM(mismatchedItems, -10)).toBeDefined();
  });

  test("extrapolation after the last item is unaffected by mismatch", () => {
    expect(profileHeightAtM(mismatchedItems, 350)).toBeDefined();
  });
});

describe("profileHeightAtM with neighbouring PVIs", () => {
  // A PVI whose end tangent sits 20 m before the current track's start (local m = -20)
  // at height 50, on an upward 5 ‰ slope.
  const prevPvi = makePviItem({
    id: "prev",
    startM: 0,
    pointM: 50,
    endM: 100,
    endHeight: 50,
    endSlope: 0.005,
  });

  test("borrows the preceding PVI's grade line on a track with no PVIs", () => {
    const prev: NeighborPvi = { item: prevPvi, localM: -20 };
    expect(profileHeightAtM([], 0, prev)).toBeCloseTo(50 + 0.005 * 20, 6);
    expect(profileHeightAtM([], 30, prev)).toBeCloseTo(50 + 0.005 * 50, 6);
  });

  test("borrows the following PVI's grade line when there is no preceding one", () => {
    // The following PVI's start tangent sits at local m = 40 at height 60, on an
    // upward 5 ‰ slope; extrapolating 10 m backwards to m = 30 drops the height 0.05.
    const next: NeighborPvi = {
      item: makePviItem({ id: "next", startHeight: 60, startSlope: 0.005 }),
      localM: 40,
    };
    expect(profileHeightAtM([], 30, undefined, next)).toBeCloseTo(
      60 - 0.005 * 10,
      6,
    );
  });

  test("draws across a PVI-less track when the neighbours' slopes match", () => {
    const prev: NeighborPvi = { item: prevPvi, localM: -20 };
    const next: NeighborPvi = {
      item: makePviItem({ id: "next", startSlope: 0.005 }),
      localM: 40,
    };
    expect(profileHeightAtM([], 30, prev, next)).toBeCloseTo(
      50 + 0.005 * 50,
      6,
    );
  });

  test("hides a PVI-less track when the neighbours' slopes disagree", () => {
    const prev: NeighborPvi = { item: prevPvi, localM: -20 };
    const next: NeighborPvi = {
      item: makePviItem({ id: "next", startSlope: -0.005 }),
      localM: 40,
    };
    expect(profileHeightAtM([], 30, prev, next)).toBeUndefined();
  });

  test("a track with own PVIs ignores a clashing preceding PVI", () => {
    // The track trusts its own PVIs: the straight before the first own item follows the
    // item's own (flat) start slope even though the preceding track's 5 ‰ end slope
    // disagrees with it.
    const own = [makePviItem({ id: "own", startM: 50, pointM: 80, endM: 110 })];
    const prev: NeighborPvi = { item: prevPvi, localM: -20 };
    expect(profileHeightAtM(own, 30, prev)).toBeCloseTo(100, 6);
  });

  test("a track with own PVIs ignores a clashing following PVI", () => {
    const own = [makePviItem({ id: "own", endM: 100, endSlope: 0.005 })];
    const clashing: NeighborPvi = {
      item: makePviItem({ id: "next", startSlope: -0.005 }),
      localM: 110,
    };
    expect(profileHeightAtM(own, 150, undefined, clashing)).toBeCloseTo(
      100 + 0.005 * 50,
      6,
    );
  });
});
