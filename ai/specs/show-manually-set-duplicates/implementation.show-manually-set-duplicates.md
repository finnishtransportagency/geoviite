# Implementation Plan: Show Manually Set Duplicates

## Overview

Currently, the UI only displays geometrically overlapping duplicate tracks (tracks that share 2+ consecutive switch points). This implementation will extend the duplicate track display to include manually set duplicates (tracks with `duplicateOf` property set), with visual indicators for manual-only duplicates.

## Current State Analysis

### Backend Functions

1. **`getLocationTrackDuplicates`** (LocationTrackService.kt:525-548)
   - Already fetches both manual duplicates (`fetchDuplicateVersions`) and switch-linked tracks
   - Combines them and passes to `getLocationTrackDuplicatesBySplitPoints`
   
2. **`getLocationTrackDuplicatesBySplitPoints`** (SwitchLocationTrackLink.kt:30-50)
   - Only returns tracks with geometric overlap (matching split points)
   - **This is the bottleneck** - it filters out manual duplicates that don't have geometric overlap

### Data Model

- **`LocationTrackDuplicate`** contains:
  - `id`, `name`, `trackNumberId`, etc.
  - **`duplicateStatus: DuplicateStatus`**
  
- **`DuplicateStatus`** contains:
  - `match`: FULL / PARTIAL / NONE
  - **`duplicateOfId`**: present only for explicit (manual) duplicates
  - `overlappingLength`: geometric overlap length

### UI Components

1. **`location-track-infobox-duplicate-track-entry.tsx`**
   - Already has logic to differentiate manual vs geometric duplicates
   - Shows INFO icon for implicit (geometric) duplicates
   - Shows ERROR/WARNING icons for various validation issues
   - **Ready to handle manual-only duplicates with NONE match**

## Problem

The issue is in `getLocationTrackDuplicatesBySplitPoints` which:
1. Receives both manual and geometric duplicate candidates
2. Only returns those with split point matches (geometric overlap)
3. Drops manual duplicates that have no geometric overlap

## Solution

Modify `getLocationTrackDuplicatesBySplitPoints` to preserve manual duplicates even when they have no geometric overlap.

## Files to Modify

### 1. **`/infra/src/main/kotlin/fi/fta/geoviite/infra/tracklayout/SwitchLocationTrackLink.kt`**

**Lines to modify:** 30-50 (function `getLocationTrackDuplicatesBySplitPoints`)

**Why:** This function currently filters out all tracks without geometric overlap. We need to:
- Keep existing geometric duplicate logic (split point matching)
- Add logic to include manual duplicates (`track.duplicateOf` is set) even when `match = NONE`
- Return `DuplicateStatus` with `match: NONE` for manual-only duplicates

**Approach:**
```kotlin
fun getLocationTrackDuplicatesBySplitPoints(
    mainTrack: LocationTrack,
    mainGeometry: LocationTrackGeometry,
    duplicateTracksAndGeometries: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<LocationTrackDuplicate> {
    val mainTrackSplitPoints = collectSplitPoints(mainGeometry)
    
    // Existing geometric duplicates logic
    val geometricDuplicates = duplicateTracksAndGeometries
        .asSequence()
        .filter { (duplicateTrack, _) -> duplicateTrack.state != LocationTrackState.DELETED }
        .flatMap { (duplicateTrack, duplicateAlignment) ->
            getLocationTrackDuplicatesBySplitPoints(
                mainTrack.id,
                mainTrackSplitPoints,
                duplicateTrack,
                duplicateAlignment,
            )
        }
        .toList()
    
    // NEW: Add manual-only duplicates (those with duplicateOf set but no geometric match)
    val geometricDuplicateIds = geometricDuplicates.map { it.second.id }.toSet()
    val manualOnlyDuplicates = duplicateTracksAndGeometries
        .filter { (track, _) -> 
            track.duplicateOf == mainTrack.id && 
            track.id !in geometricDuplicateIds &&
            track.state != LocationTrackState.DELETED
        }
        .map { (track, geometry) ->
            // Create LocationTrackDuplicate with NONE match status
            // Include duplicateOfId to indicate it's an explicit duplicate
        }
    
    return (geometricDuplicates.map { it.second } + manualOnlyDuplicates)
        .sortedWith(compareBy({ ??? }, { it.name }))
        .toList()
}
```

**Note:** Need to review the internal helper function signature to understand the exact return type and construction pattern.

### 2. **No UI changes needed**

The UI component `location-track-infobox-duplicate-track-entry.tsx` already:
- Checks `duplicateStatus.duplicateOfId !== undefined` to identify manual duplicates
- Handles `match === 'NONE'` case with ERROR-level notice "No geometry overlap"
- This will automatically display the warning icon for manual-only duplicates

## Testing Considerations

After implementation, verify:

1. **Geometric duplicates still work** - tracks sharing switch points display as before
2. **Manual duplicates show up** - tracks with `duplicateOf` set display even without geometric overlap
3. **Warning icon appears** - manual-only duplicates show appropriate notice icon
4. **Combined duplicates work** - tracks that are both manual AND geometric show correct status

## Summary

**Modified files:** 1 file
- `SwitchLocationTrackLink.kt` - extend duplicate detection logic

**Added files:** 0 files

**Key change:** Preserve manual duplicates in the duplicate list even when they lack geometric overlap, letting the UI's existing validation logic display the appropriate warnings.
