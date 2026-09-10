# Kotlin Conventions

## Error Handling in Internal Lookups

`mapNotNull` and `?: null` are for data that legitimately might not exist from a business perspective. When looking up data we know should be there (OIDs for tracks routing already found, geometry points at M values routing returned), a missing result is a bug — throw with `error("...")`, not silent null.

Pattern: `error("SomeType not found: context=$context id=$id")` for internal inconsistencies. Client errors (bad OID from caller) use `ExtOidNotFoundExceptionV1` or similar.

## Naming

Don't name a function `toX` when `X` is an existing interface or type in the codebase — it implies you're constructing that type. Use a name that describes the transformation instead (e.g. `toLayoutCoordinate` rather than `toLayoutPoint` when `LayoutPoint` is an interface).
