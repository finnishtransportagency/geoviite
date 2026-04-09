---
name: data-access-patterns
description: Guide for notable patterns in accessing data in the Geoviite project
---

- Request flow: frontend x-view.tsx -> frontend x-api.ts -> backend XController.kt -> backend XService.kt -> backend
  XDao.kt -> database
- Most database rows are versioned automatically: any change in the row produces a new version row in the version table
- Backend caching is done with row versions:
    - Non-LayoutAsset data is often cached by row-version:
        - Favor resolving versions with a single DB query
        - Mapping the version to actual objects through the cache is quick
    - LayoutAsset data is cached with LayoutRowVersion (like row-version but includes LayoutContext), fetched usually
      in one of 2 ways:
        - Context (+ id / id-list / list all) : always the current state
        - Branch & moment (+ id / id-list / list all) : the OFFICIAL state on the given moment
    - So even if it looks like n+1 fetch, it IS efficient to first resolve versions and then fetch objects for each
      separately through the cache
- Frontend caching is done with context + id + changetime with the entire cache invalidating when changetime for the
  type changes
    - In UI, prefer storing ID and push that through props to components, along with relevant changetimes
        - Actual data can be quickly re-fetched as it's in cache anyhow
        - Redux especially should not contain the full data, just the IDs
    - When changing data, fetch and update the changetime into redux via change-time-api.ts -> updates all views through
      the prop changing
