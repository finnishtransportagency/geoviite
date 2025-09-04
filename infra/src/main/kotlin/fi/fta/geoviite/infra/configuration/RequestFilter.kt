package fi.fta.geoviite.infra.configuration

import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import correlationId
import currentUser
import currentUserRole
import fi.fta.geoviite.api.configuration.ExtApiConfiguration
import fi.fta.geoviite.api.frameconverter.v1.EXT_FRAME_CONVERTER_BASE_PATH
import fi.fta.geoviite.api.tracklayout.v1.EXT_TRACK_LAYOUT_BASE_PATH
import fi.fta.geoviite.infra.SpringContextUtility
import fi.fta.geoviite.infra.authorization.AuthCode
import fi.fta.geoviite.infra.authorization.AuthName
import fi.fta.geoviite.infra.authorization.AuthorizationService
import fi.fta.geoviite.infra.authorization.DESIRED_ROLE_COOKIE_NAME
import fi.fta.geoviite.infra.authorization.ExtApiUserType
import fi.fta.geoviite.infra.authorization.Role
import fi.fta.geoviite.infra.authorization.User
import fi.fta.geoviite.infra.authorization.UserDetails
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.authorization.isValidCode
import fi.fta.geoviite.infra.environmentInfo.EnvironmentInfo
import fi.fta.geoviite.infra.error.ApiUnauthorizedException
import fi.fta.geoviite.infra.error.InvalidUiVersionException
import fi.fta.geoviite.infra.error.createErrorResponse
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.logging.apiRequest
import fi.fta.geoviite.infra.logging.apiResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.core.annotation.Order
import org.springframework.core.io.UrlResource
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URL
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.*

const val HTTP_HEADER_REMOTE_IP = "X-FORWARDED-FOR"
const val HTTP_HEADER_CORRELATION_ID = "X-Amzn-Trace-Id"
const val HTTP_HEADER_JWT_DATA = "x-iam-data"
const val HTTP_HEADER_JWT_ACCESS = "x-iam-accesstoken"
const val HTTP_HEADER_GEOVIITE_UI_VERSION = "x-geoviite-ui-version"

const val ALGORITHM_RS256 = "RS256"
const val ALGORITHM_ES256 = "ES256"

val slowRequestThreshold: Duration = Duration.ofSeconds(5)

val objectMapper = jacksonObjectMapper()

@ConditionalOnWebApplication
@Component
@Order(1)
class RequestFilter
@Autowired
constructor(
    @Value("\${geoviite.skip-auth:false}") private val skipAuth: Boolean,
    @Value("\${geoviite.jwt.validation.enabled:true}") private val validationEnabled: Boolean,
    @Value("\${geoviite.jwt.validation.jwks-url:}") private val jwksUrl: String,
    @Value("\${geoviite.jwt.validation.elb-jwt-key-url:}") private val elbJwtUrl: String,
    @Value("\${geoviite.api-root:}") private val apiRoot: String,
    @Value("\${geoviite.app-root:}") private val appRoot: String,
    private val extApi: ExtApiConfiguration,
    private val environmentInfo: EnvironmentInfo,
    private val localizationService: LocalizationService,
) : OncePerRequestFilter() {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val authorizationService: AuthorizationService by lazy { SpringContextUtility.getBean() }
    private val objectMapper: ObjectMapper by lazy { SpringContextUtility.getBean() }

    private val jwkProvider: UrlJwkProvider by lazy {
        check(jwksUrl.isNotBlank()) { "Invalid configuration: set property geoviite.jwt.validation.url" }
        UrlJwkProvider(URL("$jwksUrl/.well-known/jwks.json"))
    }

    private fun localUser(activeRole: Role, availableRoles: List<Role>): User {
        return User(
            details =
                UserDetails(
                    userName = UserName.of("LOCAL_USER"),
                    firstName = AuthName.of("Local"),
                    lastName = AuthName.of("User"),
                    organization = AuthName.of("Geoviite"),
                ),
            role = activeRole,
            availableRoles = availableRoles,
        )
    }

    private fun extApiUser(userType: ExtApiUserType): User {
        val activeRole =
            authorizationService.getRole(userType.roleCode)
                ?: throw ApiUnauthorizedException("Could not determine ext api user role.")

        val userDetails =
            when (userType) {
                ExtApiUserType.LOCAL ->
                    UserDetails(
                        userName = UserName.of("API_LOCAL"),
                        firstName = AuthName.of("Local"),
                        lastName = AuthName.of("Api User"),
                        organization = AuthName.of("Geoviite"),
                    )

                ExtApiUserType.PUBLIC ->
                    UserDetails(
                        userName = UserName.of("API_PUBLIC"),
                        firstName = AuthName.of("Public"),
                        lastName = AuthName.of("Api User"),
                        organization = AuthName.of("Geoviite"),
                    )

                ExtApiUserType.PRIVATE ->
                    UserDetails(
                        userName = UserName.of("API_PRIVATE"),
                        firstName = AuthName.of("Private"),
                        lastName = AuthName.of("Api User"),
                        organization = AuthName.of("Geoviite"),
                    )
            }

        return User(details = userDetails, role = activeRole, availableRoles = listOf(activeRole))
    }

    private val healthCheckUser by lazy {
        User(
            details = UserDetails(UserName.of("HEALTH_CHECK"), null, null, null),
            role = Role(code = AuthCode("health-check"), privileges = listOf()),
            availableRoles = listOf(),
        )
    }

    init {
        log.info(
            "Initializing request filter: " +
                "skipAuth=$skipAuth validationEnabled=$validationEnabled jwksUrl=$jwksUrl elbJwtUrl=$elbJwtUrl"
        )
    }

    fun checkUiRequestVersion(request: HttpServletRequest) {
        request
            .getHeader(HTTP_HEADER_GEOVIITE_UI_VERSION)
            ?.takeIf(String::isNotEmpty)
            ?.takeIf { requestVersion -> requestVersion != environmentInfo.releaseVersion }
            ?.run { throw InvalidUiVersionException() }
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val startTime = Instant.now()
        val requestIP = extractRequestIP(request)

        @Suppress("TooGenericExceptionCaught")
        try {
            correlationId.set(extractRequestCorrelationId(request))
            val user = getUser(request)

            currentUser.set(user.details.userName)
            currentUserRole.set(user.role.code)

            log.apiRequest(request, requestIP)
            val auth = UsernamePasswordAuthenticationToken(user, "", user.role.privileges)
            SecurityContextHolder.getContext().authentication = auth

            val path = request.requestURI

            if (path.startsWith(apiRoot)) {
                val newPath = path.replace(apiRoot, "")
                request.getRequestDispatcher(newPath).forward(request, response)
            } else if (path == "/" && appRoot.isNotBlank()) {
                // Redirect browser from server root to <url>/$appRoot/
                // (So that Geoviite UI can open without specifying the /app/ path in the bowser).
                response.status = HttpServletResponse.SC_FOUND
                response.setHeader("Location", "$appRoot/")
            } else if (path == "$appRoot/" && appRoot.isNotBlank()) {
                // Serve index.html directly from the appRoot.
                // (So that index.html is not displayed in the browser url bar).
                request.getRequestDispatcher("$appRoot/index.html").forward(request, response)
            } else {
                checkUiRequestVersion(request)
                chain.doFilter(request, response)
            }
        } catch (ex: Exception) {
            // Normally, errors handled via Spring error handling (see ApiErrorHandler.kt)
            // However, if it occurs too early in the request handling or inside the spring structures,
            // this is the only place to process it
            val errorResponse =
                createErrorResponse(
                    logger = log,
                    exception = ex,
                    requestType = inferRequestType(request.requestURI),
                    translation = localizationService.getLocalization(LocalizationLanguage.FI),
                )
            response.contentType = errorResponse.headers.contentType?.toString() ?: MediaType.APPLICATION_JSON_VALUE
            response.status = errorResponse.statusCode.value()
            response.characterEncoding = "UTF-8"
            response.writer.write(objectMapper.writeValueAsString(errorResponse.body))
            response.writer.flush()
        } finally {
            log.apiResponse(request, response, requestIP, startTime, slowRequestThreshold)
            currentUserRole.clear()
            currentUser.clear()
            correlationId.clear()
        }
    }

    private fun getUser(request: HttpServletRequest): User {
        val headers = request.headerNames.toList()

        return if (skipAuth) {
            if (isExtApiRequest(request)) {
                extApiUser(ExtApiUserType.LOCAL)
            } else {
                val availableRolesForLocalUser =
                    authorizationService.getRoles(authorizationService.defaultRoleCodeOrder)

                localUser(
                    activeRole = getActiveUserRole(request, availableRolesForLocalUser),
                    availableRoles = availableRolesForLocalUser,
                )
            }
        } else if (request.requestURI == "/actuator/health" && headers.none { h -> h.startsWith("x-iam") }) {
            healthCheckUser
        } else if (isExtApiRequest(request)) {
            determineExtApiUserOrThrow(request)
        } else {
            val content = getJwtData(request)

            log.info(
                "JWT authorization headers processed: " +
                    "validated=$validationEnabled user=${content.userDetails.userName} groups=${content.groupNames}"
            )

            val availableRoles = authorizationService.getRolesByUserGroups(content.groupNames)
            if (availableRoles.isEmpty()) {
                throw ApiUnauthorizedException(
                    "User doesn't have a valid role: userId=${content.userDetails.userName} groups=${content.groupNames}"
                )
            }

            User(
                details = content.userDetails,
                role = getActiveUserRole(request, availableRoles),
                availableRoles = availableRoles,
            )
        }
    }

    private fun getActiveUserRole(request: HttpServletRequest, availableRoles: List<Role>): Role {
        return request.cookies
            ?.firstOrNull { cookie -> cookie?.name == DESIRED_ROLE_COOKIE_NAME }
            ?.let { desiredRoleCookie ->
                val desiredRoleCode = AuthCode(desiredRoleCookie.value)

                availableRoles.find { availableRole -> availableRole.code == desiredRoleCode }
            } ?: authorizationService.getDefaultRole(availableRoles)
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
        @Suppress("TooGenericExceptionCaught")
        try {
            val algorithm =
                when (jwt.algorithm) {
                    ALGORITHM_RS256 -> getCognitoValidationAlgorithm(jwt.keyId)
                    else -> throw IllegalArgumentException("Unsupported access JWT algorithm: ${jwt.algorithm}")
                }
            JWT.require(algorithm).withIssuer(jwksUrl).build().verify(jwt)
            log.debug("JWT access token validated")
        } catch (ex: TokenExpiredException) {
            throw ApiUnauthorizedException(
                message = "JWT access token expired.",
                localizedMessageKey = "error.unauthorized.token-expired",
                cause = ex,
            )
        } catch (ex: Exception) {
            throw ApiUnauthorizedException("JWT access token validation failed.", ex)
        }
    }

    private fun validateDataToken(jwt: DecodedJWT) {
        @Suppress("TooGenericExceptionCaught")
        try {
            val algorithm =
                when (jwt.algorithm) {
                    ALGORITHM_ES256 -> getElbValidationAlgorithm(jwt.keyId)
                    else -> throw IllegalArgumentException("Unsupported data JWT algorithm: ${jwt.algorithm}")
                }
            JWT.require(algorithm).withIssuer(jwksUrl).build().verify(jwt)
            log.debug("JWT data token validated")
        } catch (ex: TokenExpiredException) {
            throw ApiUnauthorizedException(
                message = "JWT access token expired.",
                localizedMessageKey = "error.unauthorized.token-expired",
                cause = ex,
            )
        } catch (ex: Exception) {
            throw ApiUnauthorizedException("JWT data token validation failed.", ex)
        }
    }

    private fun getCognitoValidationAlgorithm(keyId: String): Algorithm {
        val jwk = jwkProvider.get(keyId)
        val publicKey =
            jwk.publicKey as? RSAPublicKey
                ?: throw IllegalArgumentException("Invalid key type: ${jwk.publicKey::class.qualifiedName}")
        check(jwk.algorithm == ALGORITHM_RS256) { "Unsupported JWK RSA algorithm: ${jwk.algorithm}" }
        return Algorithm.RSA256(publicKey, null)
    }

    private fun getElbValidationAlgorithm(keyId: String): Algorithm {
        check(elbJwtUrl.isNotBlank()) { "Configuration error: No ELB JWT key url defined" }
        val keyString = UrlResource("$elbJwtUrl/$keyId").inputStream.reader().readText()
        val keyData = unwrapPublicKey(keyString)
        val kf: KeyFactory = KeyFactory.getInstance("EC")
        val generated = kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(keyData)))
        val publicKey =
            generated as? ECPublicKey
                ?: throw IllegalArgumentException(
                    "Invalid key (expected ECPublicKey): ${generated::class.qualifiedName}"
                )
        return Algorithm.ECDSA256(publicKey, null)
    }

    private fun isExtApiRequest(request: HttpServletRequest): Boolean {
        return extApi.enabled && extApi.urlPathPrefixes.any { prefix -> request.requestURI.startsWith(prefix) }
    }

    private fun determineExtApiUserOrThrow(request: HttpServletRequest): User {
        return request
            .getHeader("x-forwarded-host")
            ?.takeIf { it.isNotEmpty() }
            ?.split(".")
            ?.firstOrNull()
            ?.let { firstSubDomain ->
                when (firstSubDomain) {
                    "avoinapi" -> extApiUser(ExtApiUserType.PUBLIC)
                    "api" -> extApiUser(ExtApiUserType.PRIVATE)

                    else ->
                        throw ApiUnauthorizedException("Could not determine integration api user type (invalid host).")
                }
            } ?: throw ApiUnauthorizedException("Could not determine integration api user type (missing host).")
    }
}

const val PUBLIC_KEY_PREFIX = "-----BEGIN PUBLIC KEY-----"
const val PUBLIC_KEY_POSTFIX = "-----END PUBLIC KEY-----"

private fun unwrapPublicKey(keyData: String): String =
    keyData.replace("\n", "").trim().drop(PUBLIC_KEY_PREFIX.length).dropLast(PUBLIC_KEY_POSTFIX.length)

private fun extractRequestIP(req: HttpServletRequest): String {
    return (req.getHeader(HTTP_HEADER_REMOTE_IP) ?: req.remoteAddr).split(",")[0]
}

private fun extractRequestCorrelationId(request: HttpServletRequest): String {
    return request.getHeader(HTTP_HEADER_CORRELATION_ID) ?: randomCorrelationId()
}

private fun randomCorrelationId(): String = "NCI-${UUID.randomUUID()}"

private fun jwtDataContent(dataToken: DecodedJWT) =
    JwtContent(
        userDetails =
            UserDetails(
                userName = UserName.of(dataToken.getMandatoryClaim(JwtClaim.USER_ID)),
                firstName = dataToken.getOptionalClaim(JwtClaim.FIRST_NAME)?.let(AuthName::of),
                lastName = dataToken.getOptionalClaim(JwtClaim.LAST_NAME)?.let(AuthName::of),
                organization = dataToken.getOptionalClaim(JwtClaim.ORGANIZATION)?.let(AuthName::of),
            ),
        groupNames = dataToken.getMandatoryClaim(JwtClaim.ROLES).let(::parseAuthCodes),
    )

private fun extractJwtToken(request: HttpServletRequest, header: String): DecodedJWT {
    val tokenString = request.getHeader(header) ?: throw ApiUnauthorizedException("JWT header is missing: $header")
    return decodeJwt(tokenString)
}

private fun decodeJwt(token: String): DecodedJWT {
    @Suppress("TooGenericExceptionCaught")
    try {
        return JWT.decode(token)
    } catch (ex: Exception) {
        throw ApiUnauthorizedException("JWT token parsing failed.", ex)
    }
}

data class JwtContent(val userDetails: UserDetails, val groupNames: List<AuthCode>)

@Suppress("unused")
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

fun DecodedJWT.getMandatoryClaim(claim: JwtClaim): String =
    getOptionalClaim(claim)
        ?: throw ApiUnauthorizedException("JWT token does not contain required claim ${claim.header}")

private fun parseAuthCodes(authCodeListing: String): List<AuthCode> {
    val authCodeStrings =
        if (authCodeListing.startsWith("[") && authCodeListing.endsWith("]")) {
            // Auth code listing seems to be EntraID-style.
            objectMapper.readValue(authCodeListing)
        } else {
            // Default to OAM-style auth code listing.
            authCodeListing.split(",")
        }

    return authCodeStrings.filter(::isValidCode).map(::AuthCode)
}

enum class GeoviiteRequestType {
    EXT_API_V1,
    INT_API,
}

fun inferRequestType(request: WebRequest): GeoviiteRequestType =
    (request as? ServletWebRequest)?.request?.requestURI?.let(::inferRequestType) ?: GeoviiteRequestType.INT_API

fun inferRequestType(requestURI: String): GeoviiteRequestType =
    when {
        requestURI.startsWith("$EXT_TRACK_LAYOUT_BASE_PATH/") -> {
            GeoviiteRequestType.EXT_API_V1
        }
        requestURI.startsWith("$EXT_FRAME_CONVERTER_BASE_PATH/") -> {
            GeoviiteRequestType.EXT_API_V1
        }
        else -> {
            GeoviiteRequestType.INT_API
        }
    }
