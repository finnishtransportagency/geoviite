package fi.fta.geoviite.infra.authorization

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.TestApi
import fi.fta.geoviite.infra.configuration.HTTP_HEADER_JWT_DATA
import fi.fta.geoviite.infra.util.Code
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import kotlin.test.assertEquals


/*
Generated via jwt.io
{
  "alg": "HS256",
  "kid": "qwer-tyui-op12-asdfghjklzxcv",
  "typ": "JWT",
  "client": "1234567890abcdefg",
  "signer": "https://localhost:5000",
  "exp": 1635781397
}

{
  "custom:rooli": "geoviite_selaaja",
  "sub": "1234567890",
  "custom:sukunimi": "Doe",
  "email_verified": "false",
  "custom:etunimi": "John",
  "custom:puhelin": "+3580000000",
  "custom:uid": "A123456",
  "custom:organisaatio": "Test Oy",
  "email": "john.doe@test.fi",
  "username": "john.doe@test.fi",
  "exp": 1635781397,
  "iss": "https://localhost:5000"
}

geoviite-test-secret
 */
const val TOKEN =
    "eyJhbGciOiJIUzI1NiIsImtpZCI6InF3ZXItdHl1aS1vcDEyLWFzZGZnaGprbHp4Y3YiLCJ0eXAiOiJKV1QiLCJjbGllbnQiOiIxMjM0NTY3ODkwYWJjZGVmZyIsInNpZ25lciI6Imh0dHBzOi8vbG9jYWxob3N0OjUwMDAiLCJleHAiOjE2MzU3ODEzOTd9.eyJjdXN0b206cm9vbGkiOiJnZW92aWl0ZV9zZWxhYWphIiwic3ViIjoiMTIzNDU2Nzg5MCIsImN1c3RvbTpzdWt1bmltaSI6IkRvZSIsImVtYWlsX3ZlcmlmaWVkIjoiZmFsc2UiLCJjdXN0b206ZXR1bmltaSI6IkpvaG4iLCJjdXN0b206cHVoZWxpbiI6IiszNTgwMDAwMDAwIiwiY3VzdG9tOnVpZCI6IkExMjM0NTYiLCJjdXN0b206b3JnYW5pc2FhdGlvIjoiVGVzdCBPeSIsImVtYWlsIjoiam9obi5kb2VAdGVzdC5maSIsInVzZXJuYW1lIjoiam9obi5kb2VAdGVzdC5maSIsImV4cCI6MTYzNTc4MTM5NywiaXNzIjoiaHR0cHM6Ly9sb2NhbGhvc3Q6NTAwMCJ9.RFlxp6lIO4HkPv6IQCPHXepHzmzJml4v_7b92qfl1EU"

@ActiveProfiles("dev", "test")
@SpringBootTest(properties = ["geoviite.jwt.validation.enabled=false", "geoviite.skip-auth=false"])
@AutoConfigureMockMvc
class AuthorizationIT @Autowired constructor(
    val authorizationDao: AuthorizationDao,
    mapper: ObjectMapper,
    mockMvc: MockMvc,
) : ITTestBase() {

    val testApi = TestApi(mapper, mockMvc)
    val user by lazy {
        User(
            details = UserDetails(
                userName = UserName("A123456"),
                firstName = AuthName("John"),
                lastName = AuthName("Doe"),
                organization = AuthName("Test Oy"),
            ),
            role = authorizationDao.getRole(Code("browser"))
        )
    }

    @Test
    fun authorizedUserGetsOwnDetailsCorrectly() {
        assertEquals(testApi.response(user), testApi.doGet(
            MockMvcRequestBuilders
                .get("/authorization/own-details")
                .header(HTTP_HEADER_JWT_DATA, TOKEN),
            HttpStatus.OK,
        ))
    }

    @Test
    fun unauthorizedUserGets401() {
        testApi.assertErrorResult(
            testApi.doGet("/authorization/my-role", HttpStatus.UNAUTHORIZED),
            "API request unauthorized: JWT header is missing: x-iam-data",
        )
    }

    @Test
    fun callWithPublicApiSucceeds() {
        assertEquals(testApi.response(OK), testApi.doGet(
            MockMvcRequestBuilders
                .get("/test-auth/public")
                .header(HTTP_HEADER_JWT_DATA, TOKEN),
            HttpStatus.OK,
        ))
    }

    @Test
    fun callWithOkPrivilegeSucceeds() {
        assertEquals(testApi.response(OK), testApi.doGet(
            MockMvcRequestBuilders
                .get("/test-auth/read")
                .header(HTTP_HEADER_JWT_DATA, TOKEN),
            HttpStatus.OK,
        ))
    }

    @Test
    fun callWithNokPrivilegeFailsWith403() {
        testApi.assertErrorResult(
            testApi.doGet(
                MockMvcRequestBuilders
                    .get("/test-auth/write")
                    .header(HTTP_HEADER_JWT_DATA, TOKEN),
                HttpStatus.FORBIDDEN),
            "Access is denied",
        )
    }

    @Test
    fun callWithNonexistingPrivilegeFailsWith403() {
        testApi.assertErrorResult(
            testApi.doGet(
                MockMvcRequestBuilders
                    .get("/test-auth/fail")
                    .header(HTTP_HEADER_JWT_DATA, TOKEN),
                HttpStatus.FORBIDDEN
            ),
            "Access is denied",
        )
    }
}
