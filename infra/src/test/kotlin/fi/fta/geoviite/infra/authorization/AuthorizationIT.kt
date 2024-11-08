package fi.fta.geoviite.infra.authorization

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TestApi
import fi.fta.geoviite.infra.configuration.HTTP_HEADER_JWT_DATA
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

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

@ActiveProfiles("dev", "test", "backend")
@SpringBootTest(properties = ["geoviite.jwt.validation.enabled=false", "geoviite.skip-auth=false"])
@AutoConfigureMockMvc
class AuthorizationIT
@Autowired
constructor(
    authorizationDao: AuthorizationDao,
    authorizationService: AuthorizationService,
    mapper: ObjectMapper,
    mockMvc: MockMvc,
) : DBTestBase() {

    val testApi = TestApi(mapper, mockMvc)

    val availableRoles = authorizationDao.getRolesByRoleCodes(listOf(AuthCode("browser")))
    val user by lazy {
        User(
            details =
                UserDetails(
                    userName = UserName.of("A123456"),
                    firstName = AuthName.of("John"),
                    lastName = AuthName.of("Doe"),
                    organization = AuthName.of("Test Oy"),
                ),
            role = authorizationService.getDefaultRole(availableRoles),
            availableRoles = availableRoles,
        )
    }

    @Test
    fun authorizedUserGetsOwnDetailsCorrectly() {
        assertEquals(testApi.response(user), testApi.doGet(getRequest("/authorization/own-details"), HttpStatus.OK))
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
        assertEquals(testApi.response(OK), testApi.doGet(getRequest("/test-auth/public"), HttpStatus.OK))
    }

    @Test
    fun callWithOkPrivilegeSucceeds() {
        assertEquals(testApi.response(OK), testApi.doGet(getRequest("/test-auth/read"), HttpStatus.OK))
    }

    @Test
    fun callWithNokPrivilegeFailsWith403() {
        testApi.assertErrorResult(testApi.doGet(getRequest("/test-auth/write"), HttpStatus.FORBIDDEN), "Access Denied")
    }

    @Test
    fun callWithNonexistingPrivilegeFailsWith403() {
        testApi.assertErrorResult(testApi.doGet(getRequest("/test-auth/fail"), HttpStatus.FORBIDDEN), "Access Denied")
    }

    private fun getRequest(url: String) =
        MockMvcRequestBuilders.get(url)
            .header(HTTP_HEADER_JWT_DATA, TOKEN)
            .characterEncoding(Charsets.UTF_8)
            .contentType(MediaType.APPLICATION_JSON)
}
