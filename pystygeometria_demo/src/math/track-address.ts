// Track addresses look like "0623+0931.105": track kilometer before the '+', meter
// distance within that kilometer after it. The km part may carry a letter suffix
// (e.g. "0623A") when km posts have been inserted between existing ones.

export interface TrackAddress {
  kmNumber: number;
  kmLetters: string;
  meters: number;
}

const ADDRESS_RE = /^\s*(\d{1,4})([A-Z]{0,2})\+(\d+(?:\.\d+)?)\s*$/;

export function parseTrackAddress(s: string): TrackAddress | undefined {
  const match = ADDRESS_RE.exec(s.toUpperCase());
  if (!match) {
    return undefined;
  }
  return {
    kmNumber: parseInt(match[1] ?? "", 10),
    kmLetters: match[2] ?? "",
    meters: parseFloat(match[3] ?? ""),
  };
}

export function formatTrackAddress(a: TrackAddress): string {
  const km = `${String(a.kmNumber).padStart(4, "0")}${a.kmLetters}`;
  return `${km}+${a.meters.toFixed(3).padStart(8, "0")}`;
}

// "0623+0994" style, as used for PVI point labels in the diagram
export function formatTrackKmPlusEvenMeters(a: TrackAddress): string {
  const km = `${String(a.kmNumber).padStart(4, "0")}${a.kmLetters}`;
  return `${km}+${String(Math.floor(a.meters)).padStart(4, "0")}`;
}

export function compareTrackAddresses(
  a: TrackAddress,
  b: TrackAddress,
): number {
  if (a.kmNumber !== b.kmNumber) {
    return a.kmNumber - b.kmNumber;
  }
  if (a.kmLetters !== b.kmLetters) {
    return a.kmLetters < b.kmLetters ? -1 : 1;
  }
  return a.meters - b.meters;
}

export interface TrackAddressRange {
  start: TrackAddress;
  end: TrackAddress;
}

// Strict overlap: ranges that merely touch at an endpoint (one track ending where the
// next begins) do not overlap, so consecutive tracks can be selected together.
export function trackAddressRangesOverlap(
  a: TrackAddressRange,
  b: TrackAddressRange,
): boolean {
  return (
    compareTrackAddresses(a.start, b.end) < 0 &&
    compareTrackAddresses(b.start, a.end) < 0
  );
}
