# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Geoviite is a web application for the Finnish Transport Infrastructure Agency (Väylävirasto). It maintains the **positioning base** (paikannuspohja) — the authoritative unified representation of Finland's national railway network — and synchronizes it to RATKO, the national rail asset registry.

### The three representations (essential mental model)

Everything in the codebase revolves around three representations of the same physical track:

| Representation | Format | Coordinate system |
|---|---|---|
| **Geometry plans** (GeometryPlan) | InfraModel/LandXML — precise lines, arcs, Clothoid spirals | Arbitrary, per-plan |
| **Positioning base / layout** (tracklayout) | Polylines, unified national network | ETRS-TM35FIN |
| **Address points** | km+meter track addresses (RATKO format) | Track address system |

Geometry plans are imported from CAD projects, **linked** to the layout by operators, then the layout is **published** and pushed to RATKO. This three-step flow (import → link → publish) is the core workflow.

## Repository Structure

- `infra/` — Kotlin/Spring Boot backend
- `ui/` — TypeScript/React/Redux frontend
- `doc/` — Architecture documentation in Finnish (Markdown)

## Tech Stack

**Backend:** Kotlin, JVM 21, Spring Boot 3 / Spring MVC / Spring Security, PostgreSQL + PostGIS, `NamedParameterJdbcTemplate` (no ORM), Flyway, Caffeine (caching), JGraphT + Dijkstra (routing), GeoTools (coordinate transforms), rtree2 (spatial index), JAXB (InfraModel/LandXML parsing), JWT auth (auth0 java-jwt + jwks-rsa), Detekt (static analysis), ktfmt

**Frontend:** React 19, Redux Toolkit + redux-persist, OpenLayers 10 (map), proj4 (coordinate transforms), i18next (localization), neverthrow, Webpack, Prettier

## Architecture

Detailed architecture docs live in the subdir CLAUDE files:
- Backend (packages, types, layout context, DB, caching, Spring profiles, auth, error handling): `infra/CLAUDE.md`
- Frontend (React/Redux structure, fetch pattern, OpenLayers): `ui/CLAUDE.md`

## Workspace Context

This repo lives inside a shared parent directory alongside `geoviite-env/` (private infrastructure repo). When Claude is started from the parent directory, all git commands must be run from within this sub-repo — prefix with `cd geoviite && git ...`.

**Never commit AWS credentials, account IDs, ARNs, or environment-specific config here.** This is a public repo. All environment config belongs in `geoviite-env/`.

Production releases tag **both** repos at the same version (e.g. `v1.19.0`). The build pipeline combines commits from both into a single Docker image — the ECR image tag encodes commit hashes from both repos.

## Git Workflow

- Branch naming: `dev/GVT-{ticket-id}-short-description` (always include a short description)
- Commit format: `GVT-123 Imperative description of what/why` — no colon after the ticket number. Use `General: ` or other colon-separated prefixes when there is no ticket. Single line only, under 72 characters — no body.

## Code Conventions (summary)

Full conventions in `CODE_CONVENTIONS.md`. Key points:

- **Language:** All code in English — no Scandinavian characters (ä, ö, å, Ä, Ö, Å) in identifiers, string values, or comments. The only exception is `@JsonProperty` values in ext-api data classes, which use the public API field names (those must still be ääkkönen-free, e.g. `"raiteen_paa"` not `"raiteen_pää"`).
- **Design:** Prefer making illegal states unrepresentable via the type system over adding runtime validation and checks.
- **Kotlin:** Favor named lambda parameters over `it` (except trivial one-liners). Prefer data classes and pure functions. Use `also { }` blocks in tests to scope assertions. SQL keywords lowercase in DAO code.
- **Kotlin — `parallelStream()`:** Collect all data (especially DB queries) into data classes first, then parallelize only CPU-bound processing. Never access the DB from parallel threads — the connection pool is limited and thread context carries user info needed for DB logging.
- **Tests:** Unit tests → `*Test.kt` (no DB). Integration tests → `*IT.kt` (real DB, no mocking). E2E tests → `*UI.kt` (Selenium). All JUnit 5. Backtick function names: `` fun `should do thing when condition`() ``. The `*IT.kt` files are often the clearest documentation of what a DAO method is supposed to do.
- **TypeScript:** No `any`. Prefer `undefined` over `null`. View components named `*View` / `*Label` etc. to distinguish from domain models.
- **TypeScript — branded types:** Use `brand()` to make the compiler distinguish types that map to the same primitive. `LocationTrackId` and `LayoutTrackNumberId` are both `String` but must not be mixed.
- **TypeScript — enum exhaustive matching:** Use `switch` + `exhaustiveMatchingGuard()` helper, not if/else chains. The compiler will warn on unhandled cases when the enum grows.
- **SQL:** Read ResultSet columns by name, not index. Named params only, never string concatenation. Prefer `timestamptz`.
- **Formatting:** `ktfmt` (backend, block indent = 4, max width = 120) and `prettier` (frontend) are enforced.
- **Packages:** Domain-first, not technical-layer. Backend: `camelCase`. Frontend files/folders: `kebab-case`.

