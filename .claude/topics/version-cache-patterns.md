# Geoviite Version Cache Patterns

## `fetchVersions(context, ids)` as an existence check

`LayoutAssetReader.fetchVersions(layoutContext, ids: List<IntId<T>>)` returns only the subset of requested ids that exist in that context — it's a set intersection backed by the version list cache. Cost: one DB change-time check at minimum; re-fetches the version list only if something changed. Use it to ask "which of these entities exist in context X?"

## Main-official as the root context

`LayoutContext.of(MainBranch.instance, PublicationState.OFFICIAL)` is the root context — it has no parent and inherits from nothing. If `fetchVersions(mainOfficial, id)` returns a result, the entity was definitively created in main, not in any design. This is the canonical check for "did this entity originate in main?"

## `fetchManyByVersion` for entity state from cache

`LayoutAssetReader.fetchManyByVersion(versions)` fetches full entities keyed by `LayoutRowVersion`. Hits the Caffeine entity cache; only queries the DB for versions not yet cached. Cheap for entities already loaded in the current request. Use when you need entity state (e.g., `entity.exists`) without a full context scan.

## Pattern: filtering design-created deletions from merge candidates

When merging a design to main, candidates include design-created entities that were deleted within the design — these should be excluded because they never existed in main. The correct approach: fetch entities by rowVersion, filter to `!entity.exists`, then keep only those for which `fetchVersions(mainOfficial, ids)` returns a version.

```kotlin
private fun <T : LayoutAsset<T>, C : PublicationCandidate<T>> List<C>.filterDesignCreatedDeletions(
    dao: LayoutAssetReader<T>,
    mainOfficialContext: LayoutContext,
): List<C> {
    val entityByVersion = dao.fetchManyByVersion(map { it.rowVersion })
    val deletedIds = mapNotNull { c -> c.id.takeIf { entityByVersion[c.rowVersion]?.exists == false } }
    if (deletedIds.isEmpty()) return this
    val mainIds = dao.fetchVersions(mainOfficialContext, deletedIds).map { it.id }.toSet()
    return filter { c -> entityByVersion[c.rowVersion]?.exists != false || c.id in mainIds }
}
```

Note: `operation == DELETE` is NOT a reliable proxy for "entity is deleted" here — `infer_operation_from_state_transition(NULL, DELETED)` returns `CREATE` (entity was never in main), so design-created deleted entities appear as operation=CREATE, not DELETE.
