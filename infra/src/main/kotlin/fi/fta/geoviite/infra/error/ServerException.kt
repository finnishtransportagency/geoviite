package fi.fta.geoviite.infra.error

class ServerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
