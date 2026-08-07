import { describe, expect, test } from "vitest";
import {
  ExtMeasureAddressPoint,
  ExtLocationTrack,
  ExtLocationTrackGeometryResponse,
  ExtLocationTrackProfileResponse,
  ExtProfilePviPoint,
} from "../api/types";
import { buildDiagramTracks } from "./diagram-model";

const track = (p: Partial<ExtLocationTrack>): ExtLocationTrack => ({
  sijaintiraide_oid: "1.2.246.578.3.10002.1",
  sijaintiraidetunnus: "TST 001",
  ratanumero: "001",
  ratanumero_oid: "1.2.246.578.3.10001.1",
  tyyppi: "pääraide",
  tila: "käytössä",
  kuvaus: "",
  omistaja: "",
  alkusijainti: { x: 0, y: 0, rataosoite: "0001+0100.000" },
  loppusijainti: { x: 0, y: 0, rataosoite: "0002+0050.000" },
  ...p,
});

const pvi = (
  paaluluku: ExtProfilePviPoint["paaluluku"],
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
  paaluluku,
});

const profile = (
  taitepisteet: ExtProfilePviPoint[],
): ExtLocationTrackProfileResponse => ({
  rataverkon_versio: "204d8b6a-d119-4aca-a400-c53aef947383",
  sijaintiraide_oid: "1.2.246.578.3.10002.1",
  koordinaatisto: "EPSG:3067",
  osoitevali: { alku: "0001+0100.000", loppu: "0002+0050.000", taitepisteet },
});

const geometry = (
  pisteet: ExtMeasureAddressPoint[],
): ExtLocationTrackGeometryResponse => ({
  rataverkon_versio: "204d8b6a-d119-4aca-a400-c53aef947383",
  sijaintiraide_oid: "1.2.246.578.3.10002.1",
  koordinaatisto: "EPSG:3067",
  osoitevali: { alku: "0001+0100.000", loppu: "0002+0050.000", pisteet },
});

const geometryEndingAtM = (
  osoitevali_m: number,
): ExtLocationTrackGeometryResponse =>
  geometry([{ x: 0, y: 0, osoitevali_m, rataosoite: "0000+0000" }]);

describe("buildDiagramTracks", () => {
  test("spans [0, the last geometry point's m-value]", () => {
    const points = [
      { x: 0, y: 0, osoitevali_m: 0, rataosoite: "0001+0100.000" },
      { x: 0, y: 0, osoitevali_m: 950, rataosoite: "0002+0050.000" },
    ];
    const tracks = buildDiagramTracks([track({})], {
      "1.2.246.578.3.10002.1": {
        profile: profile([pvi({ alku: 100, taite: 120, loppu: 140 })]),
        geometry: geometry(points),
      },
    });
    expect(tracks).toHaveLength(1);
    expect(tracks[0]?.startM).toBe(0);
    expect(tracks[0]?.endM).toBe(950);
    expect(tracks[0]?.items).toHaveLength(1);
    expect(tracks[0]?.geometryPoints).toEqual(points);
  });

  test("falls back to the PVI extent when the geometry has no points", () => {
    const tracks = buildDiagramTracks([track({})], {
      "1.2.246.578.3.10002.1": {
        profile: profile([pvi({ alku: 100, taite: 120, loppu: 140 })]),
        geometry: geometry([]),
      },
    });
    expect(tracks[0]?.endM).toBe(140);
    expect(tracks[0]?.geometryPoints).toEqual([]);
  });

  test("widens the span to cover PVI curves past the track end point", () => {
    const tracks = buildDiagramTracks([track({})], {
      "1.2.246.578.3.10002.1": {
        profile: profile([pvi({ alku: 900, taite: 940, loppu: 980 })]),
        geometry: geometryEndingAtM(950),
      },
    });
    expect(tracks[0]?.endM).toBe(980);
  });

  test("a PVI with null station values does not distort the span", () => {
    const tracks = buildDiagramTracks([track({})], {
      "1.2.246.578.3.10002.1": {
        profile: profile([
          pvi({ alku: 100, taite: 120, loppu: 140 }),
          pvi({ alku: 940, taite: null, loppu: null }),
        ]),
        geometry: geometryEndingAtM(950),
      },
    });
    expect(tracks[0]?.startM).toBe(0);
    expect(tracks[0]?.endM).toBe(950);
    expect(tracks[0]?.items.map((item) => item.pointM)).toEqual([120]);
  });

  test("a track with no response yet is left out", () => {
    expect(buildDiagramTracks([track({})], {})).toEqual([]);
  });

  test("carries neighbouring PVIs across a PVI-less track in between", () => {
    // Three continuous tracks: the middle one has no PVIs of its own and must borrow the
    // last PVI of the first track and the first PVI of the third.
    const tracks = buildDiagramTracks(
      [
        track({ sijaintiraide_oid: "a", sijaintiraidetunnus: "A" }),
        track({ sijaintiraide_oid: "b", sijaintiraidetunnus: "B" }),
        track({ sijaintiraide_oid: "c", sijaintiraidetunnus: "C" }),
      ],
      {
        a: {
          profile: {
            ...profile([pvi({ alku: 80, taite: 100, loppu: 120 })]),
            sijaintiraide_oid: "a",
          },
          geometry: geometryEndingAtM(200),
        },
        b: {
          profile: { ...profile([]), sijaintiraide_oid: "b" },
          geometry: geometryEndingAtM(50),
        },
        c: {
          profile: {
            ...profile([pvi({ alku: 20, taite: 40, loppu: 60 })]),
            sijaintiraide_oid: "c",
          },
          geometry: geometryEndingAtM(300),
        },
      },
    );

    const [a, b, c] = tracks;
    // The middle track's prev is track A's only PVI, whose end tangent (endM 120) sits
    // 80 m before B's start (A ends at 200), i.e. at local m = -80.
    expect(b?.prevItem?.item.id).toBe(a?.items[0]?.id);
    expect(b?.prevItem?.localM).toBe(-80);
    // Its next is track C's first PVI, whose start tangent sits at B's length (50) plus
    // the PVI's startM within C (20): local m = 70 (B's end meets C's start).
    expect(b?.nextItem?.item.id).toBe(c?.items[0]?.id);
    expect(b?.nextItem?.localM).toBe(70);
    // Track A reaches the same PVI on C across the whole of B: A's length (200) plus
    // B's length (50) plus the PVI's startM within C (20) puts it at local m = 270.
    expect(a?.nextItem?.item.id).toBe(c?.items[0]?.id);
    expect(a?.nextItem?.localM).toBe(270);
    // The outermost edges have no neighbour to carry from.
    expect(a?.prevItem).toBeUndefined();
    expect(c?.nextItem).toBeUndefined();
  });
});
