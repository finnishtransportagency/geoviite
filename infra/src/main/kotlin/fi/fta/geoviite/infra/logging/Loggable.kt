package fi.fta.geoviite.infra.logging

interface Loggable {
    fun toLog(): String

    fun logFormat(vararg params: Pair<String, Any?>) =
        "${this::class.simpleName}[${params.joinToString(" ") { (key, value) -> "$key=$value" }}]"
}
