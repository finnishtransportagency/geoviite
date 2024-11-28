package fi.fta.geoviite.infra.util

inline fun <reified T> assertInstanceOf(value: Any) {
    if (value !is T) {
        throw AssertionError("Expected instance of ${T::class}, but got ${value::class}")
    }
}
