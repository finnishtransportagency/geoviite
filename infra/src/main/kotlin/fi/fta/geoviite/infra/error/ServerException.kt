package fi.fta.geoviite.infra.error

open class ServerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
