package fi.fta.geoviite.infra.util

//Nulls are "last", e.g., 0, 1, 2, null
fun <T : Comparable<T>> nullsLastComparator(a: T?, b: T?) = if (a == null && b == null) 0
else if (a == null) 1
else if (b == null) -1
else a.compareTo(b)
