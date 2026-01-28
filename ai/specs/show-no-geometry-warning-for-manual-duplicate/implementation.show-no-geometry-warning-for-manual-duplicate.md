# Implementation Plan: Show No Geometry Warning for Manual Duplicate

## Overview

Currently, when a duplicate track is selected, the infobox shows which track it is a duplicate of. However, if the selected track has `duplicateOf` set but no geometric overlap with that track, **no warning icon is displayed** to indicate the lack of overlap.

This is inconsistent with the opposite scenario: when viewing a track that HAS manual duplicates, those duplicates DO show warning icons if they lack geometric overlap.

This implementation will fix the inconsistency by showing the warning icon in BOTH directions.

## Current Behavior

### Scenario A: Viewing Track 001 (has manual duplicate Track 002)
- Track 002 has `duplicateOf = Track 001`
- Track 002 has no geometric overlap with Track 001
- **Result:** Track 002 appears in Track 001's duplicate list WITH red warning icon ✅

### Scenario B: Viewing Track 002 (is manual duplicate of Track 001)  
- Track 002 has `duplicateOf = Track 001`
- Track 002 has no geometric overlap with Track 001
- **Result:** Track 001 is shown as "Duplicate Of" but WITHOUT warning icon ❌

## Problem Analysis

The issue is in the UI component **`location-track-infobox-duplicate-of.tsx`**:

**Lines 63-73:** When displaying a single `existingDuplicate` (the track that the current track is a duplicate of):
- It shows a link to the duplicate track
- It checks for **track number mismatch** and shows ERROR icon if different
- **It does NOT check for geometric overlap** (`duplicateStatus.match === 'NONE'`)

This differs from how duplicates are displayed in the list view (lines 74-86), where each duplicate entry uses `LocationTrackInfoboxDuplicateTrackEntry` which DOES validate geometric overlap.

## Solution

Modify `location-track-infobox-duplicate-of.tsx` to check for `duplicateStatus.match === 'NONE'` when displaying the single `existingDuplicate`, and show the appropriate warning icon.

The component should show a warning icon when EITHER:
1. Track numbers are different (existing check)
2. No geometric overlap exists (new check)

## Files to Modify

### **`/ui/src/tool-panel/location-track/location-track-infobox-duplicate-of.tsx`**

**Lines to modify:** 63-73 (the `existingDuplicate` rendering block)

**Why:** Add validation for geometric overlap (`duplicateStatus.match === 'NONE'`) similar to how it's done in `LocationTrackInfoboxDuplicateTrackEntry`.

**Changes:**

1. **Import the validation function** (or duplicate its logic inline)
   - The validation logic exists in `location-track-infobox-duplicate-track-entry.tsx` lines 124-140
   - We can either import the validation function or replicate the check inline

2. **Add geometric overlap validation**
   - Check if `existingDuplicate.duplicateStatus.match === 'NONE'`
   - If true, show ERROR icon with tooltip about non-overlapping geometry
   - This should be checked ALONGSIDE (not replace) the track number mismatch check

3. **Update the JSX rendering** (lines 63-73):

```tsx
const geometryOverlapWarning = 
    existingDuplicate?.duplicateStatus.match === 'NONE'
        ? t('tool-panel.location-track.non-overlapping-duplicate-tooltip', {
              trackName: targetLocationTrack.name,
              otherTrackName: existingDuplicate.name,
          })
        : '';

const hasTrackNumberMismatch = 
    currentTrackNumberId && 
    currentTrackNumberId !== existingDuplicate?.trackNumberId;

const hasGeometryMismatch = 
    existingDuplicate?.duplicateStatus.match === 'NONE';

const showWarningIcon = hasTrackNumberMismatch || hasGeometryMismatch;
const warningTooltip = [duplicateTrackNumberWarning, geometryOverlapWarning]
    .filter(s => s)
    .join('\n');

return existingDuplicate ? (
    <span title={warningTooltip}>
        <LocationTrackLink
            locationTrackId={existingDuplicate.id}
            locationTrackName={existingDuplicate.name}
        />
        &nbsp;
        {showWarningIcon && (
            <LocationTrackDuplicateInfoIcon level={'ERROR'} />
        )}
    </span>
) : ...
```

**Key points:**
- Reuse existing `LocationTrackDuplicateInfoIcon` component (already imported)
- Use existing translation key `'tool-panel.location-track.non-overlapping-duplicate-tooltip'`
- Maintain existing track number mismatch logic
- Show icon if EITHER condition is true
- Combine both tooltip messages if both conditions are true

## Files NOT Modified

**No backend changes needed** - The data structure already contains all necessary information:
- `LocationTrackDuplicate.duplicateStatus.match` indicates geometric overlap status
- Backend already correctly identifies `NONE` match for manual duplicates without geometry

**No other UI components need changes** - The duplicate list view (`LocationTrackInfoboxDuplicateTrackEntry`) already handles this correctly.

## Testing Considerations

After implementation, verify:

1. **Manual duplicate with no overlap** - Selected duplicate track shows warning icon next to its "Duplicate Of" reference
2. **Manual duplicate with partial overlap** - No additional warning (existing behavior preserved)
3. **Track number mismatch still works** - Original error icon logic still functions
4. **Both warnings together** - If BOTH issues exist (no overlap AND different track numbers), icon appears with combined tooltip
5. **Geometric duplicates unchanged** - Tracks that are duplicates only through switch connections still work as before

## Summary

**Modified files:** 1 file
- `location-track-infobox-duplicate-of.tsx` - Add geometric overlap validation to single duplicate display

**Added files:** 0 files

**Key change:** Bring consistency to duplicate warning display by checking for `duplicateStatus.match === 'NONE'` when showing the "Duplicate Of" field, matching the behavior already present in the duplicate list view.
