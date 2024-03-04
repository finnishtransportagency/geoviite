package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.TestApi
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test", "nodb")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class MainLayoutContextConversionTest @Autowired constructor(
    mapper: ObjectMapper,
    mockMvc: MockMvc,
) {

    val testApi = TestApi(mapper, mockMvc)

    @Test
    fun `Jackson correctly formats MainLayoutContexts inside JSON`() {
        assertEquals("\"L_OFFICIAL\"", testApi.response(MainLayoutContext.OFFICIAL))
        assertEquals("\"L_DRAFT\"", testApi.response(MainLayoutContext.DRAFT))
        assertEquals(
            "{\"context\":\"L_OFFICIAL\",\"type\":\"main\"}",
            testApi.response(MainContextTestObject(MainLayoutContext.OFFICIAL)),
        )
        assertEquals(
            "{\"context\":\"L_OFFICIAL\",\"type\":\"baseclass\"}",
            testApi.response(LayoutContextTestObject(MainLayoutContext.OFFICIAL)),
        )
        assertEquals(
            "{\"context\":\"L_DRAFT\",\"type\":\"main\"}",
            testApi.response(MainContextTestObject(MainLayoutContext.DRAFT)),
        )
        assertEquals(
            "{\"context\":\"L_DRAFT\",\"type\":\"baseclass\"}",
            testApi.response(LayoutContextTestObject(MainLayoutContext.DRAFT)),
        )
    }

    @Test
    fun `Jackson correctly formats DesignLayoutContexts inside JSON`() {
        assertEquals(
            "\"D_OFFICIAL_INT_321\"",
            testApi.response(DesignLayoutContext(PublicationState.OFFICIAL, IntId(321))),
        )
        assertEquals(
            "\"D_DRAFT_INT_123\"",
            testApi.response(DesignLayoutContext(PublicationState.DRAFT, IntId(123))),
        )
        assertEquals(
            "{\"context\":\"D_OFFICIAL_INT_654\",\"type\":\"design\"}",
            testApi.response(
                DesignContextTestObject(DesignLayoutContext(PublicationState.OFFICIAL, IntId(654))),
            ),
        )
        assertEquals(
            "{\"context\":\"D_OFFICIAL_INT_987\",\"type\":\"baseclass\"}",
            testApi.response(
                LayoutContextTestObject(DesignLayoutContext(PublicationState.OFFICIAL, IntId(987))),
            ),
        )
        assertEquals(
            "{\"context\":\"D_DRAFT_INT_456\",\"type\":\"design\"}",
            testApi.response(
                DesignContextTestObject(DesignLayoutContext(PublicationState.DRAFT, IntId(456))),
            ),
        )
        assertEquals(
            "{\"context\":\"D_DRAFT_INT_789\",\"type\":\"baseclass\"}",
            testApi.response(
                LayoutContextTestObject(DesignLayoutContext(PublicationState.DRAFT, IntId(789))),
            ),
        )
    }

    @Test
    fun `main layout context in path with parent type works`() {
        assertEquals(
            testApi.response(LayoutContextTestObject(MainLayoutContext.OFFICIAL)),
            testApi.doGet("/layout-context-test/L_OFFICIAL", HttpStatus.OK),
        )
        assertEquals(
            testApi.response(LayoutContextTestObject(MainLayoutContext.DRAFT)),
            testApi.doGet("/layout-context-test/L_DRAFT", HttpStatus.OK),
        )
    }

    @Test
    fun `design layout context in path with parent type works`() {
        assertEquals(
            testApi.response(LayoutContextTestObject(DesignLayoutContext(PublicationState.OFFICIAL, IntId(123)))),
            testApi.doGet("/layout-context-test/D_OFFICIAL_INT_123", HttpStatus.OK),
        )
        assertEquals(
            testApi.response(LayoutContextTestObject(DesignLayoutContext(PublicationState.DRAFT, IntId(321)))),
            testApi.doGet("/layout-context-test/D_DRAFT_INT_321", HttpStatus.OK),
        )
    }

    @Test
    fun `main layout context in path with specific type works`() {
        assertEquals(
            testApi.response(MainContextTestObject(MainLayoutContext.OFFICIAL)),
            testApi.doGet("/layout-context-test/main/L_OFFICIAL", HttpStatus.OK),
        )
        assertEquals(
            testApi.response(MainContextTestObject(MainLayoutContext.DRAFT)),
            testApi.doGet("/layout-context-test/main/L_DRAFT", HttpStatus.OK),
        )
    }

    @Test
    fun `design layout context in path with specific type works`() {
        assertEquals(
            testApi.response(DesignContextTestObject(DesignLayoutContext(PublicationState.OFFICIAL, IntId(123)))),
            testApi.doGet("/layout-context-test/design/D_OFFICIAL_INT_123", HttpStatus.OK),
        )
        assertEquals(
            testApi.response(DesignContextTestObject(DesignLayoutContext(PublicationState.DRAFT, IntId(321)))),
            testApi.doGet("/layout-context-test/design/D_DRAFT_INT_321", HttpStatus.OK),
        )
    }

    @Test
    fun `main layout context in argument with parent type works`() {
        assertEquals(
            testApi.response(LayoutContextTestObject(MainLayoutContext.OFFICIAL)),
            testApi.doGet("/layout-context-test/arg?context=L_OFFICIAL", HttpStatus.OK),
        )
        assertEquals(
            testApi.response(LayoutContextTestObject(MainLayoutContext.DRAFT)),
            testApi.doGet("/layout-context-test/arg?context=L_DRAFT", HttpStatus.OK),
        )
    }

    @Test
    fun `design layout context in argument with parent type works`() {
        assertEquals(
            testApi.response(LayoutContextTestObject(DesignLayoutContext(PublicationState.OFFICIAL, IntId(123)))),
            testApi.doGet("/layout-context-test/arg?context=D_OFFICIAL_INT_123", HttpStatus.OK),
        )
        assertEquals(
            testApi.response(LayoutContextTestObject(DesignLayoutContext(PublicationState.DRAFT, IntId(321)))),
            testApi.doGet("/layout-context-test/arg?context=D_DRAFT_INT_321", HttpStatus.OK),
        )
    }

    @Test
    fun `main layout context in argument with specific type works`() {
        assertEquals(
            testApi.response(MainContextTestObject(MainLayoutContext.OFFICIAL)),
            testApi.doGet("/layout-context-test/arg/main?context=L_OFFICIAL", HttpStatus.OK),
        )
        assertEquals(
            testApi.response(MainContextTestObject(MainLayoutContext.DRAFT)),
            testApi.doGet("/layout-context-test/arg/main?context=L_DRAFT", HttpStatus.OK),
        )
    }

    @Test
    fun `design layout context in argument with specific type works`() {
        assertEquals(
            testApi.response(DesignContextTestObject(DesignLayoutContext(PublicationState.OFFICIAL, IntId(123)))),
            testApi.doGet("/layout-context-test/arg/design?context=D_OFFICIAL_INT_123", HttpStatus.OK),
        )
        assertEquals(
            testApi.response(DesignContextTestObject(DesignLayoutContext(PublicationState.DRAFT, IntId(321)))),
            testApi.doGet("/layout-context-test/arg/design?context=D_DRAFT_INT_321", HttpStatus.OK),
        )
    }

    @Test
    fun `main layout context in body with parent type works`() {
        assertEquals(
            testApi.response(LayoutContextTestObject(MainLayoutContext.OFFICIAL)),
            testApi.doPost(
                "/layout-context-test/body",
                LayoutContextTestObject(MainLayoutContext.OFFICIAL),
                HttpStatus.OK,
            ),
        )
        assertEquals(
            testApi.response(LayoutContextTestObject(MainLayoutContext.DRAFT)),
            testApi.doPost(
                "/layout-context-test/body",
                LayoutContextTestObject(MainLayoutContext.DRAFT),
                HttpStatus.OK,
            ),
        )
    }

    @Test
    fun `design layout context in body with parent type works`() {
        assertEquals(
            testApi.response(DesignContextTestObject(DesignLayoutContext(PublicationState.OFFICIAL, IntId(123)))),
            testApi.doPost(
                "/layout-context-test/body",
                DesignContextTestObject(DesignLayoutContext(PublicationState.OFFICIAL, IntId(123))),
                HttpStatus.OK,
            ),
        )
        assertEquals(
            testApi.response(DesignContextTestObject(DesignLayoutContext(PublicationState.DRAFT, IntId(321)))),
            testApi.doPost(
                "/layout-context-test/body",
                DesignContextTestObject(DesignLayoutContext(PublicationState.DRAFT, IntId(321))),
                HttpStatus.OK,
            ),
        )
    }

    @Test
    fun `main layout context in body with specific type works`() {
        assertEquals(
            testApi.response(MainContextTestObject(MainLayoutContext.OFFICIAL)),
            testApi.doPost(
                "/layout-context-test/body/main",
                MainContextTestObject(MainLayoutContext.OFFICIAL),
                HttpStatus.OK,
            ),
        )
        assertEquals(
            testApi.response(MainContextTestObject(MainLayoutContext.DRAFT)),
            testApi.doPost(
                "/layout-context-test/body/main",
                MainContextTestObject(MainLayoutContext.DRAFT),
                HttpStatus.OK,
            ),
        )
    }

    @Test
    fun `design layout context in body with specific type works`() {
        assertEquals(
            testApi.response(DesignContextTestObject(DesignLayoutContext(PublicationState.OFFICIAL, IntId(123)))),
            testApi.doPost(
                "/layout-context-test/body/design",
                DesignContextTestObject(DesignLayoutContext(PublicationState.OFFICIAL, IntId(123))),
                HttpStatus.OK,
            ),
        )
        assertEquals(
            testApi.response(DesignContextTestObject(DesignLayoutContext(PublicationState.DRAFT, IntId(321)))),
            testApi.doPost(
                "/layout-context-test/body/design",
                DesignContextTestObject(DesignLayoutContext(PublicationState.DRAFT, IntId(321))),
                HttpStatus.OK,
            ),
        )
    }
}
