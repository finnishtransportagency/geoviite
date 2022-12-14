package fi.fta.geoviite.infra.configuration

import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.SpringContextUtility
import fi.fta.geoviite.infra.authorization.*
import fi.fta.geoviite.infra.error.ApiUnauthorizedException
import fi.fta.geoviite.infra.error.createErrorResponse
import fi.fta.geoviite.infra.logging.apiRequest
import fi.fta.geoviite.infra.logging.apiResponse
import fi.fta.geoviite.infra.util.Code
import fi.fta.geoviite.infra.util.isValidCode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.core.annotation.Order
import org.springframework.core.io.UrlResource
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URL
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.*
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


const val CORRELATION_ID_HEADER = "correlationId"
const val USER_HEADER = "user"
const val ROLE_HEADER = "role"

const val HTTP_HEADER_REMOTE_IP = "X-FORWARDED-FOR"
const val HTTP_HEADER_CORRELATION_ID = "X-Amzn-Trace-Id"
const val HTTP_HEADER_JWT_DATA = "x-iam-data"
const val HTTP_HEADER_JWT_ACCESS = "x-iam-accesstoken"

const val ALGORITHM_RS256 = "RS256"
const val ALGORITHM_ES256 = "ES256"

@ConditionalOnWebApplication
@Component
@Order(1)
class RequestFilter @Autowired constructor(
    @Value("\${geoviite.skip-auth:false}") private val skipAuth: Boolean,
    @Value("\${geoviite.jwt.validation.enabled:true}") private val validationEnabled: Boolean,
    @Value("\${geoviite.jwt.validation.jwks-url:}") private val jwksUrl: String,
    @Value("\${geoviite.jwt.validation.elb-jwt-key-url:}") private val elbJwtUrl: String,
) : OncePerRequestFilter() {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val authorizationService: AuthorizationService by lazy { SpringContextUtility.getBean() }
    private val objectMapper: ObjectMapper by lazy { SpringContextUtility.getBean() }

    private val jwkProvider: UrlJwkProvider by lazy {
        if (jwksUrl.isBlank()) {
            throw IllegalStateException("Invalid configuration: set property geoviite.jwt.validation.url")
        }
        UrlJwkProvider(URL("$jwksUrl/.well-known/jwks.json"))
    }
    private val localUser by lazy {
        User(
            details = UserDetails(
                userName = UserName("LOCAL_USER"),
                firstName = AuthName("Local"),
                lastName = AuthName("User"),
                organization = AuthName("Geoviite"),
            ),
            role = authorizationService.getRole(Code("operator")),
        )
    }
    private val healthCheckUser by lazy {
        User(
            details = UserDetails(UserName("HEALTH_CHECK"), null, null, null),
            role = Role(
                code = Code("health-check"),
                name = AuthName("Service Health Check"),
                privileges = listOf(),
            )
        )
    }

    init {
        log.info("Initializing request filter: " +
                "skipAuth=$skipAuth validationEnabled=$validationEnabled jwksUrl=$jwksUrl elbJwtUrl=$elbJwtUrl")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val startTime = Instant.now()
        val requestIP = extractRequestIP(request)

        try {
            MDC.put(CORRELATION_ID_HEADER, extractRequestCorrelationId(request))

            val user = getUser(request)
            MDC.put(USER_HEADER, user.details.userName.toString())
            MDC.put(ROLE_HEADER, user.role.code.toString())

            log.apiRequest(request, requestIP)
            val auth = UsernamePasswordAuthenticationToken(user, "", user.role.privileges)
            SecurityContextHolder.getContext().authentication = auth
            chain.doFilter(request, response)
        } catch (ex: Exception) {
            val errorResponse = createErrorResponse(log, ex)
            response.contentType = errorResponse.headers.contentType?.toString() ?: MediaType.APPLICATION_JSON_VALUE
            response.status = errorResponse.statusCode.value()
            response.writer.write(objectMapper.writeValueAsString(errorResponse.body))
            response.writer.flush()
        } finally {
            log.apiResponse(request, response, requestIP, startTime)
            MDC.remove(CORRELATION_ID_HEADER)
            MDC.remove(USER_HEADER)
            MDC.remove(ROLE_HEADER)
        }
    }

    private fun getUser(request: HttpServletRequest): User {
        val headers = request.headerNames.toList()
        return if (skipAuth) {
            localUser
        } else if (request.requestURI == "/actuator/health" && headers.none { h -> h.startsWith("x-iam") }) {
            healthCheckUser
        } else {
            val content = getJwtData(request)
            log.info("JWT authorization headers processed: " +
                    "validated=$validationEnabled user=${content.userDetails.userName} groups=${content.groupNames}")
            val role = authorizationService.getRoleByUserGroups(content.groupNames) ?: throw ApiUnauthorizedException(
                "User doesn't have a valid role: userId=${content.userDetails.userName} groups=${content.groupNames}"
            )
            User(content.userDetails, role)
        }
    }

    private fun getJwtData(request: HttpServletRequest): JwtContent {
        val dataJwt = extractJwtToken(request, HTTP_HEADER_JWT_DATA)
        if (validationEnabled) {
            validateAccessToken(extractJwtToken(request, HTTP_HEADER_JWT_ACCESS))
            validateDataToken(dataJwt)
        }
        return jwtDataContent(dataJwt)
    }

    private fun validateAccessToken(jwt: DecodedJWT) {
        try {
            val algorithm = when (jwt.algorithm) {
                ALGORITHM_RS256 -> getCognitoValidationAlgorithm(jwt.keyId)
                else -> throw IllegalArgumentException("Unsupported access JWT algorithm: ${jwt.algorithm}")
            }
            JWT.require(algorithm).withIssuer(jwksUrl).build().verify(jwt)
            log.debug("JWT access token validated")
        }
        catch(ex: TokenExpiredException) {
            throw ApiUnauthorizedException("JWT access token expired.", localizedMessageKey="error.unauthorized.token-expired")
        }
        catch (ex: Exception) {
            throw ApiUnauthorizedException("JWT access token validation failed.", ex)
        }
    }

    private fun validateDataToken(jwt: DecodedJWT) {
        try {
            val algorithm = when (jwt.algorithm) {
                ALGORITHM_ES256 -> getElbValidationAlgorithm(jwt.keyId)
                else -> throw IllegalArgumentException("Unsupported data JWT algorithm: ${jwt.algorithm}")
            }
            JWT.require(algorithm).withIssuer(jwksUrl).build().verify(jwt)
            log.debug("JWT data token validated")
        } catch(ex: TokenExpiredException) {
            throw ApiUnauthorizedException("JWT access token expired.", localizedMessageKey="error.unauthorized.token-expired")
        } catch (ex: Exception) {
            throw ApiUnauthorizedException("JWT data token validation failed.", ex)
        }
    }

    private fun getCognitoValidationAlgorithm(keyId: String): Algorithm {
        val jwk = jwkProvider.get(keyId)
        val publicKey = jwk.publicKey as? RSAPublicKey
            ?: throw IllegalArgumentException("Invalid key type: ${jwk.publicKey::class.qualifiedName}")
        if (jwk.algorithm != ALGORITHM_RS256) {
            throw IllegalArgumentException("Unsupported JWK RSA algorithm: ${jwk.algorithm}")
        }
        return Algorithm.RSA256(publicKey, null)
    }

    private fun getElbValidationAlgorithm(keyId: String): Algorithm {
        if (elbJwtUrl.isBlank()) throw IllegalStateException("Configuration error: No ELB JWT key url defined")
        val keyString = UrlResource("$elbJwtUrl/$keyId").inputStream.reader().readText()
        val keyData = unwrapPublicKey(keyString)
        val kf: KeyFactory = KeyFactory.getInstance("EC")
        val generated = kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(keyData)))
        val publicKey = generated as? ECPublicKey
            ?: throw IllegalArgumentException("Invalid key (expected ECPublicKey): ${generated::class.qualifiedName}")
        return Algorithm.ECDSA256(publicKey, null)
    }
}

const val PUBLIC_KEY_PREFIX = "-----BEGIN PUBLIC KEY-----"
const val PUBLIC_KEY_POSTFIX = "-----END PUBLIC KEY-----"
private fun unwrapPublicKey(keyData: String): String = keyData
    .replace("\n", "")
    .trim()
    .drop(PUBLIC_KEY_PREFIX.length)
    .dropLast(PUBLIC_KEY_POSTFIX.length)

private fun extractRequestIP(req: HttpServletRequest): String {
    return (req.getHeader(HTTP_HEADER_REMOTE_IP) ?: req.remoteAddr).split(",")[0]
}

private fun extractRequestCorrelationId(request: HttpServletRequest): String {
    return request.getHeader(HTTP_HEADER_CORRELATION_ID) ?: randomCorrelationId()
}

private fun randomCorrelationId(): String = "NCI-${UUID.randomUUID()}"

private fun jwtDataContent(dataToken: DecodedJWT) = JwtContent(
    userDetails = UserDetails(
        userName = UserName(dataToken.getMandatoryClaim(JwtClaim.USER_ID)),
        firstName = dataToken.getOptionalClaim(JwtClaim.FIRST_NAME)?.let(::AuthName),
        lastName = dataToken.getOptionalClaim(JwtClaim.LAST_NAME)?.let(::AuthName),
        organization = dataToken.getOptionalClaim(JwtClaim.ORGANIZATION)?.let(::AuthName),
    ),
    groupNames = dataToken.getMandatoryClaim(JwtClaim.ROLES)
        .split(",")
        .filter(::isValidCode)
        .map(::Code),
)

private fun extractJwtToken(request: HttpServletRequest, header: String): DecodedJWT {
    val tokenString = request.getHeader(header)
        ?: throw ApiUnauthorizedException("JWT header is missing: $header")
    return decodeJwt(tokenString)
}

private fun decodeJwt(token: String): DecodedJWT {
    try {
        return JWT.decode(token)
    } catch (ex: Exception) {
        throw ApiUnauthorizedException("JWT token parsing failed.", ex)
    }
}

data class JwtContent(val userDetails: UserDetails, val groupNames: List<Code>)

enum class JwtClaim(val header: String) {
    ROLES("custom:rooli"),
    FIRST_NAME("custom:etunimi"),
    LAST_NAME("custom:sukunimi"),
    PHONE("custom:puhelin"),
    EMAIL("email"),
    ORGANIZATION("custom:organisaatio"),
    USER_ID("custom:uid"),
    USER_NAME("username"),
}

fun DecodedJWT.getOptionalClaim(claim: JwtClaim): String? = claims[claim.header]?.asString()
fun DecodedJWT.getMandatoryClaim(claim: JwtClaim): String = getOptionalClaim(claim)
    ?: throw ApiUnauthorizedException("JWT token does not contain required claim ${claim.header}")
