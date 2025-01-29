package fi.fta.geoviite.infra.util

fun <K, V, R> mapNonNullValues(map: Map<K, V>, transform: (Map.Entry<K, V>) -> R?): Map<K, R> =
    map.entries.mapNotNull { e -> transform(e)?.let { r -> e.key to r } }.toMap()
