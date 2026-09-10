# Backend (infra/)

## Package Structure

Package root: `fi.fta.geoviite.infra`

**Layering per domain:** `Controller → Service(s) → DAO(s)`
- Controllers terminate REST requests; zero business logic. External API controllers also carry Swagger/OpenAPI annotations.
- Services hold business logic and orchestrate DAOs.
- DAOs are the only classes that touch the database — they contain SQL and result set mapping. Never mock the DB in tests.
- Use `@GeoviiteService` (not `@Service`) on service classes — it hooks an AOP aspect that logs calls at DEBUG level.

Controllers must follow REST path conventions: use nouns for resources and HTTP methods for actions, no verbs in paths. A path targets a resource (a domain object, a collection, or a virtual resource like "validity" or "linking") — not an action. Use query parameters to filter collections rather than inventing new resource paths.

**Domain packages (by size/importance):**
- `tracklayout/` — The national track layout (~22k lines, ~94 files). Core entities: `LocationTrack`, `ReferenceLine`, `LayoutSwitch`, `LayoutKmPost`, `LayoutTrackNumber`. Most other packages depend on this.
- `publication/` — Publication workflow, ~50 validation functions in `PublicationValidation.kt`, RATKO change log. `PublicationDao.kt` (~2900 lines) is the largest and most complex file.
- `geometry/` — InfraModel plan import and geometry elements.
- `ratko/` — Outbound push to RATKO (one-way, async, scheduled). Exports in dependency order: track numbers → reference lines → location tracks → switches → km posts.
- `linking/` — Links geometry plan segments to layout tracks. `SwitchFittingService.kt` (~1000 lines) scores candidate switch joint positions.
- `geocoding/` — Bidirectional address↔coordinate conversion. `Geocoding.kt` (~1200 lines).
- `split/` — Splitting a location track into multiple tracks.
- `inframodel/` — LandXML parsing and three-level validation (parse error / validation error / warning).
- `projektivelho/` — Pull-based inbound integration; polls for new InfraModel files for operator review.
- `common/` — Shared value types: `DomainId<T>`, `LayoutContext`, `LayoutBranch`, `PublicationState`, `TrackNumber`, `KmNumber`, etc.
- `math/` — Geometry primitives: `Clothoid`, `BoundingBox`, coordinate math.

External versioned API: `fi.fta.geoviite.api/` (48 files, `v1`) — separate `ext-api` Spring profile, read-only geocoding/layout API (Viitekehysmuunnin / VKM).

## Type System Foundations

**`DomainId<T>` — phantom-typed IDs** (`common/DomainId.kt`)

```kotlin
sealed class DomainId<T>
data class IntId<T>(val intValue: Int)                     : DomainId<T>()
data class StringId<T>(val stringValue: String)            : DomainId<T>()
data class IndexedId<T>(val parentId: Int, val index: Int) : DomainId<T>()
```

`T` is a phantom type. `IntId<LocationTrack>` and `IntId<LayoutSwitch>` are compile-time incompatible even though both hold an `Int`. Wire format: `INT_42`, `STR_uuid`, `IDX_5_2`.

**`RowVersion<T>` / `LayoutRowVersion<T>`** — cache keys. `RowVersion(id, version)` is immutable: same version = same data. Used as the primary Caffeine cache key throughout.

## Layout Context System (the most pervasive concept)

Every layout entity exists in a *context* = `LayoutBranch` × `PublicationState`. The tree:

```
main_official   ← authoritative, exported to RATKO
  └─ main_draft          ← workspace for layout edits
design_official ← published design plan (N of these)
  └─ design_draft        ← workspace for a design plan
```

Child context objects shadow parent context objects within their subtree. **Always pass `LayoutContext` explicitly to DAO queries** — forgetting to thread context through a new code path is the most common logic bug in this codebase.

SQL string form: `main_official`, `main_draft`, `{designId}_official`, `{designId}_draft`.

**Asset existence has three distinct notions:**
- Business-logic lifecycle state: based on `state`, `state_category` or similar. `includeDeleted` parameters and `LayoutAsset#exists` reference this.
- Actual existence in a given context at a given time. An asset that only ever existed in a draft context can be fully deleted — it leaves a trace in the version table with the `deleted` flag set.
- Cancellation from design: setting `design_asset_state = 'CANCELLED'` on an asset row. For most purposes treated as if the row doesn't exist, but publication validation treats it as an ordinary publishable change.

Avoid `value!!` outside tests — prefer `getOrThrow`-style functions when you know something exists, `requireNotNull(value) { "message" }` when enforcing a precondition, or `value?.let { }` when null is acceptable. `value!!` is fine in tests.

Keep JDBC named params declared close to the SQL that uses them (inline in the same function), not as separate constants elsewhere.

## Database Schemas

Five schemas managed by Flyway (270 migrations in `infra/src/main/resources/db/migration/`):

| Schema | Contents |
|---|---|
| `common` | Switch library, coordinate systems, roles/privileges |
| `geometry` | Imported InfraModel plans, alignments, elements |
| `layout` | Positioning base: tracks, switches, km posts, reference lines, graph nodes/edges |
| `publication` | Publication records, change log, RATKO push queue |
| `integrations` | RATKO push state, ProjektiVelho polling state |

**Audit trail via triggers:** Every table has a `*_version` companion table populated automatically by DB triggers — never write to version tables from application code. Every write records `change_time`, `change_user`, `expiry_time`, `deleted`. Nothing is hard-deleted.

`expiry_time` is the one mutable field in version rows (set when the next version is created). Do not read `expiry_time` into cached objects.

**Flyway migrations — prefer SQL over Kotlin:** SQL migrations are checksummed by Flyway on the file content; any post-apply change is caught at startup. Kotlin (`BaseJavaMigration`) migrations are checksummed on the compiled class only — if they reference a Kotlin enum or constant that changes elsewhere in the codebase, Flyway won't detect it and the migration silently runs with different semantics on a fresh DB. Use SQL by default. Reach for a Kotlin migration only when the logic genuinely can't be expressed in SQL (e.g. XML parsing, complex coordinate transforms). Existing examples: `V84` (coordinate conversion), `V152` (XPath extraction from stored InfraModel XML).

## Caching

Two mechanisms:
1. **Spring `@Cacheable`** — for simple single-item caches. Only works when called through the Spring-proxied instance (not self-calls within the same bean).
2. **Caffeine cache directly in DAOs/services** — for complex multi-fetch patterns. Standard pattern: `fetchVersions(layoutContext)` (cheap, always hits DB) → `fetchMany(versions)` (cache hit for known versions, DB only for misses). `CachePreloadService` pre-warms caches on startup and refreshes periodically.

## Spring Profiles

| Profile | Purpose |
|---|---|
| `backend` | Main application |
| `ext-api` | External VKM API (separate deployable) |
| `static-fileserver` | Serves bundled frontend |
| `noauth` | Disables JWT auth (local dev / Docker Compose) |
| `dev` | Local development config |
| `e2e` | End-to-end test config |
| `datareset` | Enables data reset endpoint |

## Authorization

Spring Security `@PreAuthorize` on controller methods. Privilege constants in `authorization/Privileges.kt` (e.g., `AUTH_BASIC`, `AUTH_VIEW_LAYOUT`, `AUTH_EDIT_LAYOUT`, `AUTH_EDIT_GEOMETRY_FILE`, `AUTH_API_FRAME_CONVERTER`). The `noauth` profile disables JWT entirely.

## Error Handling

Centralized in `error/ApiErrorHandler.kt`. No try/catch in individual controllers — exceptions propagate and are mapped to HTTP responses. Domain validation throws `ClientException` (4xx) or `ServerException` (5xx). Constructor validation via Kotlin `init` blocks automatically becomes a 4xx. See `doc/virhekasittely.md`.

## Key Files for Complex Subsystems

Read these only when working in the relevant area — don't read upfront:

| File | Topic |
|---|---|
| `geocoding/Geocoding.kt` (~1200 lines) | Geocoding / reverse geocoding math |
| `publication/PublicationDao.kt` (~2900 lines) | Publication version pinning, change log, RATKO diff |
| `publication/PublicationValidation.kt` (~1400 lines) | ~50 validation functions; scan names, read individual ones as needed |
| `tracklayout/Routing.kt` | JGraphT-based routing |
| `linking/switches/SwitchFittingService.kt` (~1000 lines) | Switch geometry fitting/scoring |

## Detailed References

Read these when working in the relevant area — don't load upfront:

| File | When to read |
|---|---|
| `.claude/topics/db-migrations.md` | Writing or reviewing Flyway migrations |
| `.claude/topics/ext-api.md` | Working in `fi.fta.geoviite.api` |
| `.claude/topics/kotlin-tooling.md` | Build commands, ktfmt, Detekt |
| `.claude/topics/kotlin-conventions.md` | Internal error handling patterns, naming |
| `.claude/topics/test-strategy.md` | Writing tests, choosing between unit/IT/mock |
| `.claude/topics/version-cache-patterns.md` | Working with `LayoutAssetReader`, cache patterns, design-to-main merges |

## Architecture Documentation

Architecture docs in `doc/` (Finnish):
- `doc/tietomalli.md` — Data model overview
- `doc/paikannuspohjan_kontekstit.md` — Layout context system
- `doc/tietokanta.md` — Database versioning and audit
- `doc/valimuisti.md` — Caching architecture
- `doc/ratkovienti.md` — RATKO push integration
- `doc/rajapintapalvelu.md` — External API versioning rules
- `doc/virhekasittely.md` — Error handling
