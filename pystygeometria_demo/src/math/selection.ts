import { ExtLocationTrack, LocationTrackType } from "../api/types";
import {
  compareTrackAddresses,
  parseTrackAddress,
  TrackAddressRange,
  trackAddressRangesOverlap,
} from "./track-address";

export function locationTrackAddressRange(
  track: ExtLocationTrack,
): TrackAddressRange | undefined {
  const start =
    track.alkusijainti && parseTrackAddress(track.alkusijainti.rataosoite);
  const end =
    track.loppusijainti && parseTrackAddress(track.loppusijainti.rataosoite);
  return start && end ? { start, end } : undefined;
}

// The location tracks to offer in the list: those overlapping the user's address range
// selection, plus any already-selected tracks (so a selection never becomes impossible
// to undo by narrowing the range). Sorted by start address.
export function listedLocationTracks(
  tracks: ExtLocationTrack[],
  range: TrackAddressRange | undefined,
  selectedOids: ReadonlySet<string>,
  typeFilter?: readonly LocationTrackType[],
): ExtLocationTrack[] {
  const typeSet =
    typeFilter && typeFilter.length > 0 ? new Set(typeFilter) : undefined;
  return tracks
    .filter((track) => {
      if (selectedOids.has(track.sijaintiraide_oid)) {
        return true;
      }
      if (typeSet && !typeSet.has(track.tyyppi)) {
        return false;
      }
      const trackRange = locationTrackAddressRange(track);
      return (
        !!range && !!trackRange && trackAddressRangesOverlap(range, trackRange)
      );
    })
    .sort((a, b) => {
      const aRange = locationTrackAddressRange(a);
      const bRange = locationTrackAddressRange(b);
      if (!aRange || !bRange) {
        return aRange ? -1 : bRange ? 1 : 0;
      }
      return compareTrackAddresses(aRange.start, bRange.start);
    });
}

// Tracks that cannot be selected because they overlap an already-selected track (the
// selected tracks must form a non-overlapping cover).
export function disabledLocationTrackOids(
  tracks: ExtLocationTrack[],
  selectedOids: ReadonlySet<string>,
): Set<string> {
  const selectedRanges = tracks
    .filter((track) => selectedOids.has(track.sijaintiraide_oid))
    .map(locationTrackAddressRange)
    .filter((range): range is TrackAddressRange => !!range);

  const disabled = new Set<string>();
  for (const track of tracks) {
    if (selectedOids.has(track.sijaintiraide_oid)) {
      continue;
    }
    const range = locationTrackAddressRange(track);
    if (!range) {
      disabled.add(track.sijaintiraide_oid);
      continue;
    }
    if (
      selectedRanges.some((selected) =>
        trackAddressRangesOverlap(selected, range),
      )
    ) {
      disabled.add(track.sijaintiraide_oid);
    }
  }
  return disabled;
}
