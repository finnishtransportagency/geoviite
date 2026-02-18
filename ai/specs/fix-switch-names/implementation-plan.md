# Implementation Plan: Fix Switch Names

## Overview
This implementation plan details how to add a bulk switch name fixing tool to Geoviite. The implementation is divided into two phases as specified in the specs.

## Status
- ✅ **Phase 1 COMPLETE** - Switch display in selection panel modified
- ⏳ **Phase 2 PENDING** - Name fixing tool/dialog

---

## Phase 1: Modify Switch Display in Selection Panel ✅ COMPLETE

### Backend Changes

#### 1. Create new endpoint: `GET /track-layout/switches/{branch}/{publicationState}/area-summary`

**File:** `infra/src/main/kotlin/fi/fta/geoviite/infra/tracklayout/LayoutSwitchController.kt`

**Why:** Create a new endpoint that returns both the count of switches in an area and optionally the switch details if count is within limit. This optimizes the UI by avoiding loading unnecessary data when zoomed out.

**Details:**
- Add new endpoint method `getSwitchesAreaSummary`
- Path parameters:
  - `@PathVariable(LAYOUT_BRANCH) branch: LayoutBranch` - Layout branch
  - `@PathVariable(PUBLICATION_STATE) publicationState: PublicationState` - Publication state
- Request parameters:
  - `@RequestParam("bbox") bbox: BoundingBox` (required) - The visible map area
  - `@RequestParam("maxSwitches") maxSwitches: Int` (required) - Maximum number of switches to return details for
  - `@RequestParam("includeSwitchesWithNoJoints") includeSwitchesWithNoJoints: Boolean` (default: false)
- Returns: `SwitchAreaSummary` data class containing:
  - `totalCount: Int` - Total number of switches in the area
  - `switches: List<LayoutSwitch>` - Switch details if totalCount <= maxSwitches, empty array otherwise
- Authorization: `@PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)`

#### 2. Create response model: `SwitchAreaSummary`

**File:** `infra/src/main/kotlin/fi/fta/geoviite/infra/tracklayout/LayoutSwitchController.kt` (or separate model file)

**Why:** Structured response type for the new endpoint.

**Details:**
```kotlin
data class SwitchAreaSummary(
    val totalCount: Int,
    val switches: List<LayoutSwitch>
)
```

#### 3. Add service method to support the endpoint

**File:** `infra/src/main/kotlin/fi/fta/geoviite/infra/tracklayout/LayoutSwitchService.kt`

**Why:** Business logic to filter switches by bounding box and return count/details based on limit.

**Details:**
- Add method `getSwitchesInArea(layoutContext: LayoutContext, bbox: BoundingBox, maxSwitches: Int, includeSwitchesWithNoJoints: Boolean): SwitchAreaSummary`
- Logic:
  1. Fetch all switches in the context using `listWithStructure(layoutContext, includeDeleted = false)`
  2. Filter switches by bounding box (check if switch location is within bbox)
  3. Count filtered switches
  4. If count <= maxSwitches, include switch details; otherwise return empty array for switches
  5. Return SwitchAreaSummary

### Frontend Changes

#### 4. Update API client

**File:** `ui/src/track-layout/layout-switch-api.ts`

**Why:** Add function to call the new backend endpoint.

**Details:**
- Add new function `getSwitchesAreaSummary(bbox: BoundingBox, layoutContext: LayoutContext, maxSwitches: number): Promise<SwitchAreaSummary>`
- Add TypeScript interface:
  ```typescript
  export type SwitchAreaSummary = {
      totalCount: number;
      switches: LayoutSwitch[];
  }
  ```

#### 5. Update Redux state to track switch count and area

**File:** `ui/src/track-layout/track-layout-slice.ts` (or appropriate slice)

**Why:** Store the total switch count for display in the panel header.

**Details:**
- Add state properties:
  - `switchesInViewportCount: number | null` - Total switches in visible area
  - `switchesInViewport: LayoutSwitch[]` - Switch details when available (empty array if too many)
- Add action to update these values when viewport changes

#### 6. Create hook to fetch switch area summary on viewport change

**File:** `ui/src/selection-panel/switch-panel/use-switch-area-summary.ts` (new file)

**Why:** Encapsulate the logic for fetching switch data based on viewport with debouncing.

**Details:**
- Create custom hook `useSwit chAreaSummary(viewport: MapViewport, layoutContext: LayoutContext)`
- Implement debouncing (e.g., 300ms delay after viewport settles)
- Determine maxSwitches based on zoom level:
  - If switches layer is visible (zoomed in enough): maxSwitches = 30
  - Otherwise: maxSwitches = 0 (only get count)
- Call `getSwitchesAreaSummary` API
- Dispatch action to update Redux state with results

#### 7. Update SwitchPanel component to display count and conditional content

**File:** `ui/src/selection-panel/switch-panel/switch-panel.tsx`

**Why:** Display total switch count in header and show switches or "zoom closer" message based on data availability.

**Details:**
- Accept new props:
  - `totalCount: number | null` - Total switches in viewport
  - `switches: LayoutSwitch[]` - Switch details (empty array if too many)
- Update render logic:
  - Show count in header/title area: "Switches ({totalCount})"
  - If `totalCount > 0` and `switches.length === 0` (too many): show "Zoom closer" message
  - If `switches.length > 0`: show switch badges as before (max 30)
  - If `totalCount === 0`: show "No results" message

#### 8. Update SelectionPanel container to use new hook and pass data

**File:** `ui/src/selection-panel/selection-panel.tsx`

**Why:** Connect the new hook to the SwitchPanel component.

**Details:**
- Use `useSwitchAreaSummary` hook
- Pass `totalCount` and conditional switches to SwitchPanel component
- Remove old direct switch fetching logic if it exists

#### 9. Add translations

**File:** `ui/public/locales/fi/translation.json` and `ui/public/locales/en/translation.json`

**Why:** Support Finnish and English translations for new UI text.

**Details:**
- Ensure "selection-panel.zoom-closer" translation exists
- Ensure "selection-panel.no-results" translation exists

---

## Phase 2: Name Fixing Tool/Dialog

### Backend Changes

#### 10. Create endpoint: `GET /track-layout/switches/{branch}/{publicationState}/preview-name-fixes`

**File:** `infra/src/main/kotlin/fi/fta/geoviite/infra/tracklayout/LayoutSwitchController.kt`

**Why:** Preview which switch names would be fixed (multiple consecutive spaces → single space) without applying changes.

**Details:**
- Path parameters:
  - `@PathVariable(LAYOUT_BRANCH) branch: LayoutBranch` - Layout branch
  - `@PathVariable(PUBLICATION_STATE) publicationState: PublicationState` - Publication state
- Request parameters:
  - `@RequestParam("bbox") bbox: BoundingBox` (required) - The visible map area
- Returns: `List<SwitchNameFixPreview>`
- Authorization: `@PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)`
- Logic: Find all switches in area that have names with multiple consecutive spaces
- Data class:
  ```kotlin
  data class SwitchNameFixPreview(
      val switchId: IntId<LayoutSwitch>,
      val currentName: SwitchName,
      val fixedName: SwitchName
  )
  ```

#### 11. Create endpoint: `POST /track-layout/switches/{branch}/draft/fix-names`

**File:** `infra/src/main/kotlin/fi/fta/geoviite/infra/tracklayout/LayoutSwitchController.kt`

**Why:** Apply name fixes by creating draft versions of switches with corrected names.

**Details:**
- Path parameters:
  - `@PathVariable(LAYOUT_BRANCH) branch: LayoutBranch` - Layout branch (must be draft context)
- Request body: `@RequestBody switchIds: List<IntId<LayoutSwitch>>` - IDs of switches to fix
- Returns: `List<IntId<LayoutSwitch>>` - IDs of updated switches
- Authorization: `@PreAuthorize(AUTH_EDIT_LAYOUT)`
- Logic: For each switch, create draft with name where multiple consecutive spaces are replaced with single space

#### 12. Add service methods for name fixing

**File:** `infra/src/main/kotlin/fi/fta/geoviite/infra/tracklayout/LayoutSwitchService.kt`

**Why:** Business logic to detect and fix switch names.

**Details:**
- Add `previewNameFixes(layoutContext: LayoutContext, bbox: BoundingBox): List<SwitchNameFixPreview>`
  - Filters switches in area
  - Identifies switches with multiple consecutive spaces
  - Returns preview list
- Add `fixSwitchNames(branch: LayoutBranch, switchIds: List<IntId<LayoutSwitch>>): List<IntId<LayoutSwitch>>`
  - For each switch ID, fetch current switch
  - Apply name fix (replace `\\s{2,}` with single space)
  - Create draft version using existing update mechanism
  - Return list of updated IDs
- Add utility function `fixSwitchName(name: SwitchName): SwitchName`
  - Use regex to replace multiple consecutive spaces with single space

### Frontend Changes

#### 13. Add API functions for name fixing

**File:** `ui/src/track-layout/layout-switch-api.ts`

**Why:** Client functions to call preview and fix endpoints.

**Details:**
- Add TypeScript interfaces:
  ```typescript
  export type SwitchNameFixPreview = {
      switchId: LayoutSwitchId;
      currentName: string;
      fixedName: string;
  }
  ```
- Add function `previewSwitchNameFixes(bbox: BoundingBox, layoutContext: LayoutContext): Promise<SwitchNameFixPreview[]>`
- Add function `fixSwitchNames(branch: LayoutBranch, switchIds: LayoutSwitchId[]): Promise<LayoutSwitchId[]>`

#### 14. Create FixSwitchNamesDialog component

**File:** `ui/src/selection-panel/switch-panel/fix-switch-names-dialog.tsx` (new file)

**Why:** Dialog to preview and confirm switch name fixes. This component owns the entire name-fixing flow including the backend call.

**Details:**
- Props:
  - `isOpen: boolean`
  - `onClose: () => void`
  - `onSuccess: () => void` - Callback when fixes are successfully applied (for refreshing parent data)
  - `previews: SwitchNameFixPreview[]`
  - `layoutBranch: LayoutBranch` - Needed for the fix API call
- UI elements:
  - Title: "Fix Switch Names"
  - Subtitle showing count: "X switch names will be fixed"
  - Scrollable list showing:
    - Current name (strikethrough style)
    - Arrow icon
    - Fixed name (bold)
  - Buttons:
    - "Cancel" - closes dialog
    - "Fix Names" - handles the fix operation internally, shows loading state
- Internal logic:
  - When "Fix Names" clicked:
    - Extract switch IDs from previews
    - Call `fixSwitchNames` API with layoutBranch and switch IDs
    - Show success notification
    - Call `onSuccess()` callback
    - Close dialog
  - Handle error states with error notifications
- Use existing dialog components from `geoviite-design-lib` if available

#### 15. Add menu button to SwitchPanel header

**File:** `ui/src/selection-panel/switch-panel/switch-panel.tsx`

**Why:** Add three-dot menu button with "Fix switch names" option.

**Details:**
- Add menu button (three dots icon) next to switch count in header
- Menu contains single item: "Fix switch names ({totalCount})"
- Only show menu if totalCount > 0
- On click, open FixSwitchNamesDialog

#### 16. Add dialog state and logic to container

**File:** `ui/src/selection-panel/selection-panel.tsx`

**Why:** Manage dialog state and preview data fetching.

**Details:**
- Add state for dialog open/closed
- Add state for preview data
- Add handler for opening dialog:
  - Fetch preview using current viewport bbox and layoutContext
  - Store preview data in state
  - Open dialog
- Add success handler (passed to dialog):
  - Refresh switch data
  - Update change times to trigger re-fetch
  - Dialog will handle closing itself after calling this callback

#### 17. Add translations

**Files:** `ui/public/locales/fi/translation.json` and `ui/public/locales/en/translation.json`

**Why:** Support Finnish and English for all new UI text.

**Details:**
- Add translations for:
  - "fix-switch-names.menu-item": "Fix switch names ({count})" / "Korjaa vaihteiden nimet ({count})"
  - "fix-switch-names.dialog-title": "Fix Switch Names" / "Korjaa vaihteiden nimet"
  - "fix-switch-names.count-message": "{count} switch names will be fixed" / "{count} vaihteen nimi muuttuu"
  - "fix-switch-names.confirm-button": "Fix Names" / "Korjaa nimet"
  - "fix-switch-names.cancel-button": "Cancel" / "Peruuta"
  - "fix-switch-names.success-message": "Switch names fixed successfully" / "Vaihteiden nimet korjattu"

---

## Testing Considerations

### Phase 1 Testing:
- Verify switch count displays correctly as you zoom in/out
- Verify switches appear when zoomed in close enough (≤30 switches visible)
- Verify "Zoom closer" message appears when >30 switches in area
- Verify debouncing works (no excessive API calls)
- Test with different layout contexts (draft/official, different branches)

### Phase 2 Testing:
- Create test switches with multiple consecutive spaces in names
- Verify preview shows correct before/after names
- Verify only switches with multiple spaces appear in preview
- Verify fix operation creates drafts correctly
- Verify fixed switches have single spaces only
- Test cancel functionality
- Test with various bbox sizes and switch counts
- Verify normal publication workflow works after fixes

---

## Summary of Files

### New Files:
1. `ui/src/selection-panel/switch-panel/use-switch-area-summary.ts` - Custom hook
2. `ui/src/selection-panel/switch-panel/fix-switch-names-dialog.tsx` - Dialog component

### Modified Files:

**Backend:**
1. `infra/src/main/kotlin/fi/fta/geoviite/infra/tracklayout/LayoutSwitchController.kt` - Add 3 endpoints
2. `infra/src/main/kotlin/fi/fta/geoviite/infra/tracklayout/LayoutSwitchService.kt` - Add business logic methods

**Frontend:**
3. `ui/src/track-layout/layout-switch-api.ts` - Add API client functions
4. `ui/src/track-layout/track-layout-slice.ts` - Add state for switch count/area
5. `ui/src/selection-panel/switch-panel/switch-panel.tsx` - Update UI for count and menu
6. `ui/src/selection-panel/selection-panel.tsx` - Integrate new hook and dialog logic
7. `ui/public/locales/fi/translation.json` - Add Finnish translations
8. `ui/public/locales/en/translation.json` - Add English translations

### Total: 2 new files, 8 modified files
