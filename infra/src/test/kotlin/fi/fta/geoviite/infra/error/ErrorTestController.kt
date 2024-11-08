package fi.fta.geoviite.infra.hello

import com.fasterxml.jackson.annotation.JsonCreator
import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.error.ServerException
import org.springframework.web.bind.annotation.*

data class ErrorTestParam(val value: Int) {
    @JsonCreator constructor(stringValue: String) : this(stringValue.toInt())

    init {
        if (value <= 0) throw InputValidationException("ErrorTestParam value too small", ErrorTestParam::class, "test")
    }
}

data class ErrorTestBody @JsonCreator constructor(val name: String, val value: Int) {
    init {
        if (value <= 0) throw InputValidationException("ErrorTestBody value too small", ErrorTestParam::class, "test")
    }
}

data class ErrorTestResponse(val message: String = "Request success")

const val ERROR_TEST_URL = "/error-test"

@GeoviiteController(ERROR_TEST_URL)
class ErrorTestController {

    @GetMapping("/path/{variable}")
    fun requestWithPathVariable(@PathVariable("variable") value: ErrorTestParam): ErrorTestResponse {
        return ErrorTestResponse()
    }

    @GetMapping("/param")
    fun requestWithParam(@RequestParam("param") value: ErrorTestParam): ErrorTestResponse {
        return ErrorTestResponse()
    }

    @PostMapping("/body")
    fun requestWithBody(@RequestBody body: ErrorTestBody): ErrorTestResponse {
        return ErrorTestResponse()
    }

    @GetMapping("/illegal")
    fun illegal(): ErrorTestResponse {
        throw IllegalStateException("Unhandled error")
    }

    @GetMapping("/illegal-java")
    fun illegalJava(): ErrorTestResponse {
        throw java.lang.IllegalStateException("Unhandled error")
    }

    @GetMapping("/client")
    fun clientException(): ErrorTestResponse {
        throw InputValidationException("Client error", ErrorTestParam::class, "test")
    }

    @GetMapping("/server")
    fun serverException(): ErrorTestResponse {
        throw ServerException("Server error")
    }

    @GetMapping("/wrapped")
    fun serverExceptionWrappedInClientException(): ErrorTestResponse {
        throw InputValidationException("Client error", ErrorTestParam::class, "test", ServerException("Server error"))
    }
}
