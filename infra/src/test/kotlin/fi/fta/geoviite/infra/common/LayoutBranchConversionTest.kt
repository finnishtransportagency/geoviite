package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.TestApi
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "nodb", "backend")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class LayoutBranchConversionTest @Autowired constructor(mapper: ObjectMapper, mockMvc: MockMvc) {

    val testApi = TestApi(mapper, mockMvc)

    @Test
    fun `Jackson correctly formats MainBranch inside JSON`() {
        assertEquals("\"MAIN\"", testApi.response(LayoutBranch.main))
        assertEquals(
            "{\"branch\":\"MAIN\",\"type\":\"main\"}",
            testApi.response(MainBranchTestObject(LayoutBranch.main)),
        )
        assertEquals(
            "{\"branch\":\"MAIN\",\"type\":\"baseclass\"}",
            testApi.response(LayoutBranchTestObject(LayoutBranch.main)),
        )
    }

    @Test
    fun `Jackson correctly formats DesignBranch inside JSON`() {
        assertEquals("\"DESIGN_INT_321\"", testApi.response(LayoutBranch.design(IntId(321))))
        assertEquals(
            "{\"branch\":\"DESIGN_INT_654\",\"type\":\"design\"}",
            testApi.response(DesignBranchTestObject(LayoutBranch.design(IntId(654)))),
        )
        assertEquals(
            "{\"branch\":\"DESIGN_INT_987\",\"type\":\"baseclass\"}",
            testApi.response(LayoutBranchTestObject(LayoutBranch.design(IntId(987)))),
        )
    }

    @Test
    fun `main layout branch in path works`() {
        assertEquals(testApi.response(LayoutBranch.main), testApi.doGet("$LAYOUT_TEST_URL/MAIN", HttpStatus.OK))
        assertEquals(testApi.response(LayoutBranch.main), testApi.doGet("$LAYOUT_TEST_URL/main/MAIN", HttpStatus.OK))
    }

    @Test
    fun `design layout branch in path works`() {
        assertEquals(
            testApi.response(LayoutBranch.design(IntId(123))),
            testApi.doGet("$LAYOUT_TEST_URL/DESIGN_INT_123", HttpStatus.OK),
        )
        assertEquals(
            testApi.response(LayoutBranch.design(IntId(321))),
            testApi.doGet("$LAYOUT_TEST_URL/DESIGN_INT_321", HttpStatus.OK),
        )
    }

    @Test
    fun `main layout branch in argument works`() {
        assertEquals(
            testApi.response(LayoutBranch.main),
            testApi.doGet("$LAYOUT_TEST_URL/arg?branch=MAIN", HttpStatus.OK),
        )
        assertEquals(
            testApi.response(LayoutBranch.main),
            testApi.doGet("$LAYOUT_TEST_URL/arg/main?branch=MAIN", HttpStatus.OK),
        )
    }

    @Test
    fun `branch parsing is case insensitive`() {
        assertEquals(LayoutBranch.main, LayoutBranch.tryParse("main"))
        assertEquals(LayoutBranch.design(IntId(123)), LayoutBranch.tryParse("design_int_123"))
    }

    @Test
    fun `design layout branch in argument works`() {
        assertEquals(
            testApi.response(LayoutBranch.design(IntId(123))),
            testApi.doGet("$LAYOUT_TEST_URL/arg?branch=DESIGN_INT_123", HttpStatus.OK),
        )
        assertEquals(
            testApi.response(LayoutBranch.design(IntId(123))),
            testApi.doGet("$LAYOUT_TEST_URL/arg/design?branch=DESIGN_INT_123", HttpStatus.OK),
        )
    }

    @Test
    fun `main layout branch in body works`() {
        val testObject = LayoutBranchTestObject(LayoutBranch.main)
        assertEquals(testApi.response(testObject), testApi.doPost("$LAYOUT_TEST_URL/body", testObject, HttpStatus.OK))
        assertEquals(
            testApi.response(testObject),
            testApi.doPost("$LAYOUT_TEST_URL/body/main", testObject, HttpStatus.OK),
        )
    }

    @Test
    fun `design layout branch in body works`() {
        val testObject = DesignBranchTestObject(LayoutBranch.design(IntId(123)))
        assertEquals(testApi.response(testObject), testApi.doPost("$LAYOUT_TEST_URL/body", testObject, HttpStatus.OK))
        assertEquals(
            testApi.response(testObject),
            testApi.doPost("$LAYOUT_TEST_URL/body/design", testObject, HttpStatus.OK),
        )
    }
}
