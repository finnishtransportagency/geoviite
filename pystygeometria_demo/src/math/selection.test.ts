import { describe, expect, test } from "vitest";
import { ExtLocationTrack } from "../api/types";
import { disabledLocationTrackOids, listedLocationTracks } from "./selection";
import { parseTrackAddress } from "./track-address";

const track = (
  oid: string,
  start?: string,
  end?: string,
): ExtLocationTrack => ({
  sijaintiraide_oid: oid,
  sijaintiraidetunnus: `track-${oid}`,
  ratanumero: "001",
  ratanumero_oid: "tn-1",
  tyyppi: "pääraide",
  tila: "käytössä",
  kuvaus: `description of ${oid}`,
  omistaja: "Väylävirasto",
  alkusijainti: start ? { x: 0, y: 0, rataosoite: start } : undefined,
  loppusijainti: end ? { x: 0, y: 0, rataosoite: end } : undefined,
});

const range = (start: string, end: string) => {
  const parsedStart = parseTrackAddress(start);
  const parsedEnd = parseTrackAddress(end);
  if (!parsedStart || !parsedEnd) {
    throw new Error("bad test range");
  }
  return { start: parsedStart, end: parsedEnd };
};

const tracks = [
  track("a", "0001+0000", "0003+0000"),
  track("b", "0002+0500", "0004+0000"), // overlaps a
  track("c", "0003+0000", "0005+0000"), // touches a, overlaps b
  track("d", "0008+0000", "0009+0000"), // far away
  track("e"), // no addresses
];

describe("listedLocationTracks", () => {
  test("lists tracks overlapping the range, sorted by start address", () => {
    const listed = listedLocationTracks(
      tracks,
      range("0002+0000", "0003+0500"),
      new Set(),
    );
    expect(listed.map((t) => t.sijaintiraide_oid)).toEqual(["a", "b", "c"]);
  });

  test("keeps selected tracks listed even outside the range", () => {
    const listed = listedLocationTracks(
      tracks,
      range("0001+0000", "0001+0500"),
      new Set(["d"]),
    );
    expect(listed.map((t) => t.sijaintiraide_oid)).toEqual(["a", "d"]);
  });

  test("lists nothing without a valid range", () => {
    expect(listedLocationTracks(tracks, undefined, new Set())).toEqual([]);
  });
});

describe("disabledLocationTrackOids", () => {
  test("disables tracks overlapping a selected track", () => {
    const disabled = disabledLocationTrackOids(tracks, new Set(["a"]));
    expect(disabled.has("b")).toBe(true);
    expect(disabled.has("a")).toBe(false);
    expect(disabled.has("d")).toBe(false);
  });

  test("does not disable a track that merely touches a selected one", () => {
    const disabled = disabledLocationTrackOids(tracks, new Set(["a"]));
    expect(disabled.has("c")).toBe(false);
  });

  test("disables tracks without addresses", () => {
    const disabled = disabledLocationTrackOids(tracks, new Set());
    expect(disabled.has("e")).toBe(true);
  });
});
