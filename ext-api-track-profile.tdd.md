# TDD Plan: ext-API endpoint sijaintiraiteiden pystygeometrialle

Source spec: `ext-api-track-profile.spec.md`

This plan covers test scenarios for the new `GET /paikannuspohja/v1/sijaintiraiteet/{sijaintiraide_oid}/pystygeometria` endpoint.

Tests are split between the service level (geometry data correctness) and the ext-API level (HTTP behavior, response structure, versioning). `GeometryService.getVerticalGeometryListing(layoutContext, locationTrackId)` is currently untested at the service level — these tests fill that gap and ensure the ext-API tests don't need to retest core geometry logic.

---

## 1. Service-level tests (geometry data correctness)

**Test file:** MODIFY — `GeometryServiceIT.kt`

The `getVerticalGeometryListing(layoutContext, locationTrackId)` overload has no tests today. Add tests here to cover the data it produces, so the ext-API layer can rely on it.

| # | Test scenario | Key assertions |
|---|---------------|----------------|
| 1 | Vertical geometry listing returns break points with correct field values for a location track with linked geometry plans | `VerticalGeometryListing` entries have expected start/end/point heights, angles, radius, tangent, linear sections, station values |
| 2 | Location track with no linked geometry plans returns an empty list | `getVerticalGeometryListing` returns `emptyList()` |
| 3 | Location track spanning multiple geometry plans returns break points from all plans | Result list includes entries from each plan covering their respective ranges |
| 4 | `overlapsAnother` is set correctly when gradient sections overlap | Break points in overlapping region have `overlapsAnother = true` |

---

## 2. Shared error/status tests

**Test file:** MODIFY — `ExtTestTrackLayoutV1IT.kt`

Register the new profile endpoint into the existing parameterized test lists so it is automatically covered by shared error scenarios. No new test methods needed.

| Change | What to do |
|--------|------------|
| `errorTests` list | Add `::setupValidLocationTrack to api.locationTrackProfile::getWithExpectedError` |
| `noContentTests` list | Add `::setupValidLocationTrack to api.locationTrackProfile::getWithEmptyBody` |

This gives the endpoint coverage for: invalid OID format (400), OID not found (404), invalid layout version format (400), layout version not found (404), unsupported coordinate system (400), and no-content when asset doesn't exist in version (204).

---

## 3. Test API helper

**Test file:** MODIFY — `ExtTrackLayoutTestApiService.kt`

Add a new `locationTrackProfile` `AssetApi` entry pointing to `/geoviite/paikannuspohja/v1/sijaintiraiteet/{oid}/pystygeometria`, with an `ExtTestLocationTrackProfileResponseV1` test data class.

---

## 4. Test data classes

**Test file:** MODIFY — `ExtTestTrackLayoutDataV1.kt`

Add test response data classes matching the profile JSON structure (`ExtTestLocationTrackProfileResponseV1`, nested address range, break point, etc.) following the existing `ExtTestLocationTrackGeometryResponseV1` pattern.

---

## 5. Feature-specific ext-API integration tests

**Test file:** NEW — `ExtLocationTrackProfileIT.kt`

Follows the same pattern as `ExtLocationTrackIT.kt`: extends `DBTestBase`, uses `ExtApiTestDataServiceV1` for data setup, and `ExtTrackLayoutTestApiService` for API calls. All tests assert top-level response fields (`rataverkon_versio`, `sijaintiraide_oid`, `koordinaatisto`) via a shared helper.

Focus: API behavior, versioning, response structure, and that key values flow through correctly from the underlying service.

| # | Test scenario | Key assertions |
|---|---------------|----------------|
| 1 | Profile API versioning works across multiple publications | Publish multiple versions; at each step, explicit version queries return correct historical data and omitting `rataverkon_versio` returns the latest |
| 2 | Response contains correctly structured break points with expected values | `pyoristyksen_alku`, `taite`, `pyoristyksen_loppu` have expected heights and locations; `pyoristyssade`, `tangentti`, `paaluluku` match source data |
| 3 | Start/end points include `kaltevuus`, intersection point does not | `pyoristyksen_alku.kaltevuus` and `pyoristyksen_loppu.kaltevuus` present; `taite` has no `kaltevuus` |
| 4 | N2000 heights equal original for N2000 source data | `korkeus_n2000` == `korkeus_alkuperäinen` |
| 5 | N43 source data returns `korkeus_n2000` as null | `korkeus_alkuperäinen` present, `korkeus_n2000` is null |
| 6 | Consecutive break points form a single address range | Single entry in `osoitevalit` |
| 7 | Gap in break points creates separate address ranges | Multiple entries in `osoitevalit` |
| 8 | `overlapsAnother` produces `kaltevuusjakso_limittain` remark | `huomiot` contains entry with correct `koodi` and `selite` |
| 9 | No overlap produces empty `huomiot` | `huomiot` is `[]` |
| 10 | `suunnitelman_korkeusasema` maps TOP_OF_SLEEPER → `"Korkeusviiva"` and TOP_OF_RAIL → `"Kiskon selkä"` | Finnish translations correct |
| 11 | Location track with no vertical geometry returns 204 | HTTP 204, empty body |

---

## Summary

| File | New / Modified | Description |
|------|----------------|-------------|
| `GeometryServiceIT.kt` | MODIFY | 4 tests for `getVerticalGeometryListing(layoutContext, locationTrackId)` — currently untested |
| `ExtTestTrackLayoutV1IT.kt` | MODIFY | Register profile endpoint in shared error/status test lists |
| `ExtTrackLayoutTestApiService.kt` | MODIFY | Add `locationTrackProfile` API helper |
| `ExtTestTrackLayoutDataV1.kt` | MODIFY | Add test response data classes for profile |
| `ExtLocationTrackProfileIT.kt` | NEW | 11 feature-specific integration tests (all assert top-level fields via shared helper) |
