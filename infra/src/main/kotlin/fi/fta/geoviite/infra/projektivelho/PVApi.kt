package fi.fta.geoviite.infra.projektivelho

import com.auth0.jwt.JWT
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.util.StringSanitizer
import fi.fta.geoviite.infra.util.UnsafeString
import fi.fta.geoviite.infra.util.assertLength
import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(PVClient::class.java)

data class PVId @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<PVId>, CharSequence by value {

    companion object {
        val allowedLength = 1..50
        const val ALLOWED_CHARACTERS = "A-Za-z0-9_\\-./"
        val sanitizer = StringSanitizer(PVId::class, ALLOWED_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: PVId): Int = value.compareTo(other.value)
}

enum class PVApiSearchState {
    kaynnistetty,
    kaynnissa,
    valmis,
    virhe,
}

data class PVApiSearchStatus(
    @JsonProperty("tila") val state: PVApiSearchState,
    @JsonProperty("hakutunniste") val searchId: PVId,
    @JsonProperty("alkuaika") val startTime: Instant,
    @JsonProperty("hakutunniste-voimassa") val validFor: Long,
)

data class PVApiDictionaryEntry(val code: PVDictionaryCode, val name: UnsafeString) {
    constructor(code: String, name: String) : this(PVDictionaryCode(code), UnsafeString(name))
}

data class PVApiSearchResult(@JsonProperty("osumat") val matches: List<PVApiMatch>)

data class PVApiMatch(
    val oid: Oid<PVDocument>,
    @JsonProperty("luontikohdeluokan-oid") val assignmentOid: Oid<PVAssignment>,
)

data class PVApiLatestVersion(
    @JsonProperty("versio") val version: PVId,
    @JsonProperty("nimi") val name: UnsafeString,
    @JsonProperty("muokattu") val changeTime: Instant,
)

data class PVApiDocumentMetadata(
    @JsonProperty("tila") val materialState: PVDictionaryCode,
    @JsonProperty("kuvaus") val description: UnsafeString?,
    @JsonProperty("laji") val materialCategory: PVDictionaryCode?,
    @JsonProperty("dokumenttityyppi") val documentType: PVDictionaryCode,
    @JsonProperty("ryhma") val materialGroup: PVDictionaryCode,
    @JsonProperty("tekniikka-alat") val technicalFields: List<PVDictionaryCode>,
    @JsonProperty("sisaltaa-henkilotietoja") val containsPersonalInfo: Boolean?,
)

data class PVApiDocument(
    @JsonProperty("tuorein-versio") val latestVersion: PVApiLatestVersion,
    @JsonProperty("metatiedot") val metadata: PVApiDocumentMetadata,
)

data class PVApiProperties(
    @JsonProperty("nimi") val name: UnsafeString,
    @JsonProperty("tila") val state: PVDictionaryCode,
)

data class PVApiProjectGroup(
    @JsonProperty("ominaisuudet") val properties: PVApiProperties,
    @JsonProperty("oid") val oid: Oid<PVProjectGroup>,
    @JsonProperty("luotu") val createdAt: Instant,
    @JsonProperty("muokattu") val modified: Instant?,
)

data class PVApiProject(
    @JsonProperty("ominaisuudet") val properties: PVApiProperties,
    @JsonProperty("oid") val oid: Oid<PVProject>,
    @JsonProperty("projektijoukko") val projectGroupOid: Oid<PVProjectGroup>?,
    @JsonProperty("luotu") val createdAt: Instant,
    @JsonProperty("muokattu") val modified: Instant?,
)

data class PVApiAssignment(
    @JsonProperty("ominaisuudet") val properties: PVApiProperties,
    @JsonProperty("oid") val oid: Oid<PVAssignment>,
    @JsonProperty("projekti") val projectOid: Oid<PVProject>,
    @JsonProperty("luotu") val createdAt: Instant,
    @JsonProperty("muokattu") val modified: Instant?,
)

data class PVSearch(val id: IntId<PVSearch>, val token: PVId, val state: PVFetchStatus, val validUntil: Instant)

enum class PVFetchStatus {
    WAITING,
    FETCHING,
    FINISHED,
    ERROR,
}

val pvBearerTokenLength = 1..5000

data class PVBearerToken @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<PVBearerToken>, CharSequence by value {

    @get:JsonIgnore val decoded by lazy { JWT.decode(value) }

    init {
        assertLength(PVBearerToken::class, value, pvBearerTokenLength)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: PVBearerToken): Int = value.compareTo(other.value)
}

enum class BearerTokenType {
    Bearer
}

data class PVAccessToken(
    @JsonProperty("access_token") val accessToken: PVBearerToken,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: BearerTokenType,
) {
    private val issueTime: Instant = accessToken.decoded.issuedAtAsInstant ?: Instant.now()

    @get:JsonIgnore
    val expireTime: Instant
        get() = accessToken.decoded.expiresAtAsInstant ?: issueTime.plusSeconds(expiresIn)

    init {
        accessToken.decoded.let { t ->
            logger.info(
                "ProjektiVelho API Bearer token: " +
                    "audience=${t.audience} " +
                    "issuer=${t.issuer} " +
                    "subject=${t.subject} " +
                    "algorithm=${t.algorithm} " +
                    "issued=${t.issuedAtAsInstant} " +
                    "notBefore=${t.notBeforeAsInstant} " +
                    "expires=${t.expiresAtAsInstant} " +
                    "claims=${t.claims}"
            )
            if (t.issuedAtAsInstant == null) {
                logger.warn("ProjektiVelho API token does not have issued time available")
            } else if (Duration.between(t.issuedAtAsInstant, Instant.now()).abs() > Duration.ofSeconds(60)) {
                logger.warn(
                    "ProjektiVelho API token is not issued in 1 minute. The server time might differ from the client."
                )
            }
            if (t.expiresAtAsInstant == null) {
                logger.warn(
                    "ProjektiVelho API token does not have expiry time available: defaulting to now()+expiresIn"
                )
            }
        }
        if (issueTime.plusSeconds(expiresIn) != expireTime) {
            logger.warn(
                "ProjektiVelho API token expiry does not match expiresIn value:" +
                    " issued=${issueTime} expires=${expireTime} expiresIn=$expiresIn"
            )
        }
    }
}
