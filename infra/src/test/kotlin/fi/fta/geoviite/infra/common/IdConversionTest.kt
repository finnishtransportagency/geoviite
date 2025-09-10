package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.TestApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.OK
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test", "nodb", "backend")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class IdConversionTest @Autowired constructor(mapper: ObjectMapper, mockMvc: MockMvc) {

    val testApi = TestApi(mapper, mockMvc)

    @Test
    fun jacksonFormatsIdCorrectlyInsideJson() {
        assertEquals("{\"id\":\"STR_asdfg\"}", successResponse(StringId("asdfg")))
        assertEquals("{\"id\":\"INT_123\"}", successResponse(IntId(123)))
        assertEquals("{\"id\":\"IDX_123_321\"}", successResponse(IndexedId(123, 321)))
    }

    @Test
    fun getWithStringIdInPathSucceeds() {
        assertEquals(successResponse(StringId("asdf")), testApi.doGet("$ID_TEST_URL/STR_asdf", OK))
    }

    @Test
    fun getWithTypedStringIdInPathSucceeds() {
        assertEquals(successResponse(StringId("asdf")), testApi.doGet("$ID_TEST_URL/string/STR_asdf", OK))
    }

    @Test
    fun getWithIntIdInPathSucceeds() {
        assertEquals(successResponse(IntId(123)), testApi.doGet("$ID_TEST_URL/INT_123", OK))
    }

    @Test
    fun getWithTypedIntIdInPathSucceeds() {
        assertEquals(successResponse(IntId(123)), testApi.doGet("$ID_TEST_URL/int/INT_123", OK))
    }

    @Test
    fun getWithIndexIdInPathSucceeds() {
        assertEquals(successResponse(IndexedId(12, 34)), testApi.doGet("$ID_TEST_URL/IDX_12_34", OK))
    }

    @Test
    fun getWithTypedIndexIdInPathSucceeds() {
        assertEquals(successResponse(IndexedId(12, 34)), testApi.doGet("$ID_TEST_URL/indexed/IDX_12_34", OK))
    }

    @Test
    fun getWithOidInPathSucceeds() {
        assertEquals(successResponse(Oid("123.456.789")), testApi.doGet("$ID_TEST_URL/oid/123.456.789", OK))
    }

    @Test
    fun getWithSridInPathSucceeds() {
        assertEquals(successResponse(Srid(12345)), testApi.doGet("$ID_TEST_URL/srid/EPSG:12345", OK))
    }

    @Test
    fun getWithUnparseableIdInPathIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ID_TEST_URL/asdf", HttpStatus.BAD_REQUEST),
            "Argument type mismatch: id (type DomainId) method 'requestWithIdPath' parameter 0",
            "Conversion failed for value \"asdf\": [String] -> [DomainId]",
        )
    }

    @Test
    fun getWithUnparseableOidInPathIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ID_TEST_URL/oid/123.asdf", HttpStatus.BAD_REQUEST),
            "Argument type mismatch: id (type Oid) method 'requestWithOidPath' parameter 0",
            "Conversion failed for value \"123.asdf\": [String] -> [Oid]",
        )
    }

    @Test
    fun getWithUnparseableSridInPathIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ID_TEST_URL/srid/123asdf", HttpStatus.BAD_REQUEST),
            "Argument type mismatch: id (type Srid) method 'requestWithSridPath' parameter 0",
            "Conversion failed for value \"123asdf\": [String] -> [Srid]",
        )
    }

    @Test
    fun getWithStringIdInArgumentSucceeds() {
        assertEquals(successResponse(StringId("asdf")), testApi.doGet("$ID_TEST_URL/id-test-arg?id=STR_asdf", OK))
    }

    @Test
    fun getWithIntIdInArgumentSucceeds() {
        assertEquals(successResponse(IntId(123)), testApi.doGet("$ID_TEST_URL/id-test-arg?id=INT_123", OK))
    }

    @Test
    fun getWithIndexIdInArgumentSucceeds() {
        assertEquals(successResponse(IndexedId(12, 34)), testApi.doGet("$ID_TEST_URL/id-test-arg?id=IDX_12_34", OK))
    }

    @Test
    fun getWithTypedStringIdInArgumentSucceeds() {
        assertEquals(
            successResponse(StringId("asdf")),
            testApi.doGet("$ID_TEST_URL/id-test-arg/string?id=STR_asdf", OK),
        )
    }

    @Test
    fun getWithTypedIntIdInArgumentSucceeds() {
        assertEquals(successResponse(IntId(123)), testApi.doGet("$ID_TEST_URL/id-test-arg/int?id=INT_123", OK))
    }

    @Test
    fun getWithTypedIndexIdInArgumentSucceeds() {
        assertEquals(
            successResponse(IndexedId(12, 34)),
            testApi.doGet("$ID_TEST_URL/id-test-arg/indexed?id=IDX_12_34", OK),
        )
    }

    @Test
    fun getWithOidInArgumentSucceeds() {
        assertEquals(successResponse(Oid("234.123.789")), testApi.doGet("$ID_TEST_URL/oid-test-arg?id=234.123.789", OK))
    }

    @Test
    fun getWithSridInArgumentSucceeds() {
        assertEquals(successResponse(Srid(23412)), testApi.doGet("$ID_TEST_URL/srid-test-arg?id=EPSG:23412", OK))
    }

    @Test
    fun getWithUnparseableIdInArgumentIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ID_TEST_URL/id-test-arg?id=asdf", HttpStatus.BAD_REQUEST),
            "Argument type mismatch: id (type DomainId) method 'requestWithIdArgument' parameter 0",
            "Conversion failed for value \"asdf\": [String] -> [DomainId]",
        )
    }

    @Test
    fun getWithUnparseableOidInArgumentIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ID_TEST_URL/oid-test-arg?id=234.asdf.567", HttpStatus.BAD_REQUEST),
            "Argument type mismatch: id (type Oid) method 'requestWithOidArgument' parameter 0",
            "Conversion failed for value \"234.asdf.567\": [String] -> [Oid]",
        )
    }

    @Test
    fun getWithUnparseableSrdInArgumentIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ID_TEST_URL/srid-test-arg?id=234asdf", HttpStatus.BAD_REQUEST),
            "Argument type mismatch: id (type Srid) method 'requestWithSridArgument' parameter 0",
            "Conversion failed for value \"234asdf\": [String] -> [Srid]",
        )
    }

    @Test
    fun postWithStringIdInBodySucceeds() {
        val body = IdTestObject(StringId("asdf"))
        assertEquals(testApi.response(body), testApi.doPost("$ID_TEST_URL/id-test-body", body, OK))
    }

    @Test
    fun postWithIntIdInBodySucceeds() {
        val body = IdTestObject(IntId(123))
        assertEquals(testApi.response(body), testApi.doPost("$ID_TEST_URL/id-test-body", body, OK))
    }

    @Test
    fun postWithIndexIdInBodySucceeds() {
        val body = IdTestObject(IndexedId(12, 34))
        assertEquals(testApi.response(body), testApi.doPost("$ID_TEST_URL/id-test-body", body, OK))
    }

    @Test
    fun postWithTypedStringIdInBodySucceeds() {
        val body = StringIdTestObject(StringId("asdf"))
        assertEquals(testApi.response(body), testApi.doPost("$ID_TEST_URL/id-test-body/string", body, OK))
    }

    @Test
    fun postWithTypedIntIdInBodySucceeds() {
        val body = IntIdTestObject(IntId(123))
        assertEquals(testApi.response(body), testApi.doPost("$ID_TEST_URL/id-test-body/int", body, OK))
    }

    @Test
    fun postWithTypedIndexIdInBodySucceeds() {
        val body = IndexedIdTestObject(IndexedId(12, 34))
        assertEquals(testApi.response(body), testApi.doPost("$ID_TEST_URL/id-test-body/indexed", body, OK))
    }

    @Test
    fun postWithOidInBodySucceeds() {
        val body = OidTestObject(Oid("1.2.3"))
        assertEquals(testApi.response(body), testApi.doPost("$ID_TEST_URL/oid-test-body", body, OK))
    }

    @Test
    fun postWithSridInBodySucceeds() {
        val body = SridTestObject(Srid(12345))
        assertEquals(testApi.response(body), testApi.doPost("$ID_TEST_URL/srid-test-body", body, OK))
    }

    @Test
    fun postWithUnparseableIdInBodyIs400() {
        testApi.assertErrorResult(
            testApi.doPostWithString("$ID_TEST_URL/id-test-body", "{\"id\": \"asdf\"}", HttpStatus.BAD_REQUEST),
            "Request body not readable",
            "Failed to instantiate Lfi/fta/geoviite/infra/common/DomainId;",
            "Invalid DomainId: \"asdf\"",
        )
    }

    @Test
    fun postWithUnparseableOidInBodyIs400() {
        testApi.assertErrorResult(
            testApi.doPostWithString("$ID_TEST_URL/oid-test-body", "{\"id\":\"1.2.3.a\"}", HttpStatus.BAD_REQUEST),
            "Request body not readable",
            "Failed to instantiate Lfi/fta/geoviite/infra/common/Oid;",
            "Input validation failed: Invalid characters in Oid: \"1.2.3.a\"",
        )
    }

    @Test
    fun postWithUnparseableSridInBodyIs400() {
        testApi.assertErrorResult(
            testApi.doPostWithString("$ID_TEST_URL/srid-test-body", "{\"id\":\"1a\"}", HttpStatus.BAD_REQUEST),
            "Request body not readable",
            "Failed to instantiate Lfi/fta/geoviite/infra/common/Srid;",
            "Input validation failed: Invalid string prefix: prefix=EPSG: value=\"1a\"",
        )
    }

    @Test
    fun `Minimal and maximal StringIds are parsed successfully`() {
        val minId = StringId<IdConversionTest>("a")
        assertEquals(minId, DomainId.parse(minId.toString()))
        assertEquals(minId, StringId.parse(minId.toString()))

        val maxId = StringId<IdConversionTest>("a".repeat(96))
        assertEquals(maxId, DomainId.parse(maxId.toString()))
        assertEquals(maxId, StringId.parse(maxId.toString()))
    }

    @Test
    fun `Minimal and maximal IntIds are parsed successfully`() {
        val minId = IntId<IdConversionTest>(1)
        assertEquals(minId, DomainId.parse(minId.toString()))
        assertEquals(minId, IntId.parse(minId.toString()))

        val maxId = IntId<IdConversionTest>(Int.MAX_VALUE)
        assertEquals(maxId, DomainId.parse(maxId.toString()))
        assertEquals(maxId, IntId.parse(maxId.toString()))
    }

    @Test
    fun `Minimal and maximal IndexedIds are parsed successfully`() {
        val minStringId = IndexedId<IdConversionTest>(1, 0)
        assertEquals(minStringId, DomainId.parse(minStringId.toString()))
        assertEquals(minStringId, IndexedId.parse(minStringId.toString()))

        val maxId = IndexedId<IdConversionTest>(Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals(maxId, DomainId.parse(maxId.toString()))
        assertEquals(maxId, IndexedId.parse(maxId.toString()))
    }

    @Test
    fun `Overlong StringIds are rejected`() {
        assertThrows<IllegalArgumentException> {
            StringId.parse<IdConversionTest>(StringId<IdConversionTest>("a".repeat(96)).toString() + "a")
        }
    }

    @Test
    fun `Overlong IntIds are rejected`() {
        assertThrows<IllegalArgumentException> {
            IntId.parse<IdConversionTest>(IntId<IdConversionTest>(Int.MAX_VALUE).toString() + "0")
        }
    }

    @Test
    fun `Overlong IndexedIds are rejected`() {
        assertThrows<IllegalArgumentException> {
            IndexedId.parse<IdConversionTest>(
                IndexedId<IdConversionTest>(Int.MAX_VALUE, Int.MAX_VALUE).toString() + "0"
            )
        }
    }

    private fun successResponse(id: DomainId<IdTestObject>): String = testApi.response(IdTestObject(id))

    private fun successResponse(id: Oid<OidTestObject>): String = testApi.response(OidTestObject(id))

    private fun successResponse(id: Srid): String = testApi.response(SridTestObject(id))
}
