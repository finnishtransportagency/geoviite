# Frontend (ui/)

## E2E Tests

E2E page models live in `infra/src/test/kotlin/fi/fta/geoviite/infra/ui/pagemodel/` — check `qa-id` selectors there whenever changing UI. E2E tests do not run on the PR; they run in the CI/CD pipeline after merge. Local testing is the only pre-merge gate.

## Architecture

Translation strings (Finnish UI text): `infra/src/main/resources/i18n/translations.fi.json`.
All UI-visible text goes here — do not hardcode Finnish strings in component code.

- Per-domain packages under `ui/src/` (e.g., `track-layout/`, `geometry/`, `linking/`, `publication/`).
- Each domain has: `*-model.ts` (types), `*-store.ts` (Redux slice), `*-api.ts` (API calls). API calls always go through `*-api.ts`; never fetch directly from components.
- Four persisted Redux slices (`trackLayout`, `infraModel`, `dataProducts`, `common`). Feature-local state (edit dialogs, linking workflow) lives in per-feature stores, not Redux.
- Store only IDs in Redux, not full objects. Objects live in `AsyncCache` (`cache/cache.ts`) keyed on `(changeTime, key)`. Change times are polled and flow through Redux to trigger re-renders.
- Map UI is in `map/` (one file per map layer). Side panels in `tool-panel/` and `selection-panel/`.

## Commands (from `ui/`)

```bash
npm start        # Dev server (webpack serve)
npm test         # Jest tests
npm run lint     # ESLint (auto-fix)
npm run build    # Production build
npm run scss     # Regenerate typed SCSS module definitions
```

Before committing, format changed files (CI runs `npm run format:check` on `ui/src`):
`git diff --name-only --relative --diff-filter=d origin/main -- src | grep -E '\.(js|jsx|ts|tsx|css|scss|html|json)$' | xargs -r npx prettier --write`

## Fetch / Cache Pattern

- Cache API results with `asyncCache` inside `*-api.ts`; invalidate via change times polled from the backend.
- Use `useLoader` (or `useLoaderWithStatus` when indicating loading state) in components — never fetch directly. Asset-specific variants exist: `useLocationTrack()`, `useSwitch()`, `useTrackNumber()`, etc.
- Prefer non-ADT fetch variants: `putNonNull()` over `putNonNullAdt()`, etc. Use ADT variants only when you need to branch on the result type.

## Redux — What Belongs and What Doesn't

Store in Redux when:

- Multiple disconnected components share the same state (e.g. map selection)
- State must survive navigation or page refresh

Do **not** store in Redux:

- State derivable from existing Redux state
- Form state initialized from fetched data (breaks on cache refresh)
- Transient UI state: dropdowns open/closed, dialog visibility, in-flight request status

## Redux — Container Component Pattern

Keep Redux selector hooks (`useCommonDataAppSelector`, `useTrackLayoutAppSelector`, ...) in the outermost container component and pass data to presentational components as props, e.g. `KmPostInfoboxContainer` → `KmPostInfobox`. Some existing views (e.g. `VerticalGeometryView`) mix the two; don't copy them.

## OpenLayers

- Only one map instance is ever created; it is saved to `window.map`.
- Reuse layer instances and stateful interactions (e.g. `Modify`) — don't recreate them on re-render.

## Map Tool Layer Visibility

When a map tool requires a layer to be visible, use `addForcedVisibleLayer` on activate and `removeForcedVisibleLayer` on deactivate — do not toggle the user's own layer menu state. `selectVisibleLayers` in `map-store.ts` unions `menuLayers` with `forcedVisibleLayers`, so the user's original toggle is restored exactly when the tool deactivates. Search `addForcedVisibleLayer` for existing examples.
