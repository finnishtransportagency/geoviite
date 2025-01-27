package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.logging.SPAN_IDS_KEY
import fi.fta.geoviite.infra.logging.copyThreadContextToReactiveResponseThread
import fi.fta.geoviite.infra.logging.withLogSpan
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.apache.logging.log4j.ThreadContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

const val MOCK_SERVER_PORT = 1080

class ThreadContextMiddlewareTest {

    private lateinit var mockServer: ClientAndServer

    @BeforeEach
    fun startMockServer() {
        mockServer = ClientAndServer.startClientAndServer(MOCK_SERVER_PORT)
        mockServer
            .`when`(HttpRequest.request().withMethod("GET").withPath("/example"))
            .respond(HttpResponse.response().withStatusCode(200).withBody("mock response"))
    }

    @AfterEach
    fun stopMockServer() {
        mockServer.stop()
    }

    @Test
    fun `ThreadContext should be restored after copyThreadContextToReactiveResponseThread()`() {
        val originalKey = "some-initial-thread-state"
        val originalValue = "foo-bar-123"

        val someOtherKey = "some-other-key"
        val someOtherValue = "some-other-value"

        ThreadContext.put(originalKey, originalValue)

        val middlewareFunc = copyThreadContextToReactiveResponseThread()
        val mockExchangeFunction = ExchangeFunction { _ ->
            Mono.just(ClientResponse.create(HttpStatusCode.valueOf(200)).body("mock response").build())
        }

        val clientRequest =
            ClientRequest.create(HttpMethod.GET, URI.create("http://localhost:$MOCK_SERVER_PORT/example")).build()

        val result =
            middlewareFunc.filter(clientRequest, mockExchangeFunction).doOnSubscribe {
                ThreadContext.put(someOtherKey, someOtherValue)

                // Overwrite the originalKey in reactive context to test that the value is also
                // restored to the previous value outside the reactive context.
                assertEquals(originalValue, ThreadContext.get(originalKey))

                val modifiedValue = originalValue + "modified"
                ThreadContext.put(originalKey, modifiedValue)

                assertEquals(modifiedValue, ThreadContext.get(originalKey))
            }

        StepVerifier.create(result).expectNextCount(1).verifyComplete()

        assertNull(ThreadContext.get(someOtherKey))
        assertEquals(originalValue, ThreadContext.get(originalKey))

        ThreadContext.clearMap()
    }

    @Test
    fun `withLogSpan works in reactor thread context`() {
        val middlewareFunc = copyThreadContextToReactiveResponseThread()
        val mockExchangeFunction = ExchangeFunction { _ ->
            Mono.just(ClientResponse.create(HttpStatusCode.valueOf(200)).body("mock response").build())
        }

        val outerSpanId = "someSpan"

        withLogSpan(spanId = outerSpanId) {
            assertEquals(outerSpanId, ThreadContext.get(SPAN_IDS_KEY), "outer span id should be set")

            val clientRequest =
                ClientRequest.create(HttpMethod.GET, URI.create("http://localhost:$MOCK_SERVER_PORT/example")).build()

            val result =
                middlewareFunc.filter(clientRequest, mockExchangeFunction).doOnSubscribe {
                    withLogSpan {
                        assertEquals(
                            true,
                            ThreadContext.get(SPAN_IDS_KEY).toString().startsWith(outerSpanId),
                            "Inner span id should start with the outer span id",
                        )

                        assertEquals(
                            false,
                            ThreadContext.get(SPAN_IDS_KEY) == outerSpanId,
                            "Inner span id should have a generated sub-span id as well",
                        )
                    }

                    assertEquals(
                        outerSpanId,
                        ThreadContext.get(SPAN_IDS_KEY),
                        "inner span id should be removed after inner log span context ends",
                    )
                }

            StepVerifier.create(result).expectNextCount(1).verifyComplete()
            assertEquals(outerSpanId, ThreadContext.get(SPAN_IDS_KEY), "outer span id should be restored")
        }

        assertEquals(null, ThreadContext.get(SPAN_IDS_KEY), "no span id's should exist outside the log span")
        ThreadContext.clearMap()
    }
}
