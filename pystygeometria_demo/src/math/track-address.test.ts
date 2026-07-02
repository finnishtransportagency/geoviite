import { describe, expect, test } from "vitest";
import {
  compareTrackAddresses,
  formatTrackAddress,
  formatTrackKmPlusEvenMeters,
  parseTrackAddress,
  trackAddressRangesOverlap,
} from "./track-address";

const address = (s: string) => {
  const parsed = parseTrackAddress(s);
  if (!parsed) {
    throw new Error(`unparseable test address ${s}`);
  }
  return parsed;
};

const range = (start: string, end: string) => ({
  start: address(start),
  end: address(end),
});

describe("parseTrackAddress", () => {
  test("parses a full address", () => {
    expect(parseTrackAddress("0623+0931.105")).toEqual({
      kmNumber: 623,
      kmLetters: "",
      meters: 931.105,
    });
  });

  test("parses km letter suffixes and whole meters", () => {
    expect(parseTrackAddress("0007A+0100")).toEqual({
      kmNumber: 7,
      kmLetters: "A",
      meters: 100,
    });
  });

  test("tolerates surrounding whitespace and lowercase letters", () => {
    expect(parseTrackAddress(" 0007a+0100.5 ")).toEqual({
      kmNumber: 7,
      kmLetters: "A",
      meters: 100.5,
    });
  });

  test("rejects garbage", () => {
    expect(parseTrackAddress("")).toBeUndefined();
    expect(parseTrackAddress("0623")).toBeUndefined();
    expect(parseTrackAddress("0623+")).toBeUndefined();
    expect(parseTrackAddress("abc+123")).toBeUndefined();
  });
});

describe("formatTrackAddress", () => {
  test("round-trips parse", () => {
    expect(formatTrackAddress(address("0623+0931.105"))).toBe("0623+0931.105");
    expect(formatTrackAddress(address("7A+1.5"))).toBe("0007A+0001.500");
  });
});

describe("formatTrackKmPlusEvenMeters", () => {
  test("floors meters", () => {
    expect(formatTrackKmPlusEvenMeters(address("0623+0994.899"))).toBe(
      "0623+0994",
    );
  });
});

describe("compareTrackAddresses", () => {
  test("orders by km, then letters, then meters", () => {
    expect(
      compareTrackAddresses(address("0001+0500"), address("0002+0000")),
    ).toBeLessThan(0);
    expect(
      compareTrackAddresses(address("0002+0000"), address("0002A+0000")),
    ).toBeLessThan(0);
    expect(
      compareTrackAddresses(address("0002+0100"), address("0002+0050")),
    ).toBeGreaterThan(0);
    expect(
      compareTrackAddresses(address("0002+0100"), address("0002+0100")),
    ).toBe(0);
  });
});

describe("trackAddressRangesOverlap", () => {
  test("detects overlap", () => {
    expect(
      trackAddressRangesOverlap(
        range("0001+0000", "0002+0000"),
        range("0001+0500", "0003+0000"),
      ),
    ).toBe(true);
  });

  test("containment is overlap", () => {
    expect(
      trackAddressRangesOverlap(
        range("0001+0000", "0005+0000"),
        range("0002+0000", "0003+0000"),
      ),
    ).toBe(true);
  });

  test("touching ranges do not overlap, so consecutive tracks can both be selected", () => {
    expect(
      trackAddressRangesOverlap(
        range("0001+0000", "0002+0000"),
        range("0002+0000", "0003+0000"),
      ),
    ).toBe(false);
  });

  test("disjoint ranges do not overlap", () => {
    expect(
      trackAddressRangesOverlap(
        range("0001+0000", "0002+0000"),
        range("0004+0000", "0005+0000"),
      ),
    ).toBe(false);
  });
});
