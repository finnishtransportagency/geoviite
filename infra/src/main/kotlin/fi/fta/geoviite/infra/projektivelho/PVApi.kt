package fi.fta.geoviite.infra.projektivelho

import PVAssignment
import PVDictionaryCode
import PVDictionaryName
import PVDocument
import PVProject
import PVProjectGroup
import com.auth0.jwt.JWT
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.util.*
import java.time.Instant
import java.util.*

// TODO Turn into actual data classes etc.
fun searchJson(date: Instant, minOid: Oid<PVDocument>?, maxCount: Int) = """
{
    "asetukset": {
        "tyyppi": "kohdeluokkahaku",
        "koko": $maxCount,
        "jarjesta": [
            [
                [
                    "aineisto/aineisto",
                    "tuorein-versio",
                    "muokattu"
                ],
                "nouseva"
            ],
            
            [
                [
                    "aineisto/aineisto",
                    "oid"
                ],
                "nouseva"
            ]
            
        ]
    },
    "lauseke": [
        "ja",
        
        [
            "tai",                
            [
                "suurempi-kuin",
                [
                    "aineisto/aineisto",
                    "tuorein-versio",
                    "muokattu"
                ],
                "$date"
            ],
            
            [
                "ja",
                [
                    "yhtasuuri",
                    [
                        "aineisto/aineisto",
                        "tuorein-versio",
                        "muokattu"
                    ],
                    "$date"
                ],
                [
                    "suurempi-kuin",
                    [
                        "aineisto/aineisto",
                        "oid"
                    ],
                    "${minOid ?: ""}"
                ]
                
            ]
        ],
        [
            "sisaltaa-tekstin",
            [
                "aineisto/aineisto",
                "tuorein-versio",
                "nimi"
            ],
            ".xml"
        ],
        [
            "joukossa",
            [
                "aineisto/aineisto",
                "metatiedot",
                "tekniikka-alat"
            ],
            [
                "tekniikka-ala/ta15"
            ]
        ],
        [
            "tai",
            [
                "yhtasuuri",
                [
                    "aineisto/aineisto",
                    "metatiedot",
                    "ryhma"
                ],
                "aineistoryhma/ar07"
            ]
        ]
        
    ],
    "kohdeluokat": [
        "aineisto/aineisto"
    ]
}
""".trimIndent()

val pvTargetCategoryLength = 1..100
val pvTargetCategoryRegex = Regex("^[A-ZÄÖÅa-zäöå0-9\\-/]+\$")
data class PVTargetCategory @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<PVTargetCategory>, CharSequence by value {
    init { assertSanitized<PVTargetCategory>(value, pvTargetCategoryRegex, pvTargetCategoryLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: PVTargetCategory): Int = value.compareTo(other.value)
}

val pvMasterSystemLength = 1..30
val pvMasterSystemRegex = Regex("^[A-ZÄÖÅa-zäöå0-9\\-/]+\$")
data class PVMasterSystem @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<PVMasterSystem>, CharSequence by value {
    init { assertSanitized<PVMasterSystem>(value, pvMasterSystemRegex, pvMasterSystemLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: PVMasterSystem): Int = value.compareTo(other.value)
}

val pvIdLength = 1..50
val pvIdRegex = Regex("^[A-Za-z0-9_\\-./]+\$")
data class PVId @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<PVId>, CharSequence by value {
    init { assertSanitized<PVId>(value, pvIdRegex, pvIdLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: PVId): Int = value.compareTo(other.value)
}

data class PVApiSearchStatus(
    @JsonProperty("tila") val state: PVDictionaryCode,
    @JsonProperty("hakutunniste") val searchId: PVId,
    @JsonProperty("alkuaika") val startTime: Instant,
    @JsonProperty("hakutunniste-voimassa") val validFor: Long
)

data class PVApiSearchResult(@JsonProperty("osumat") val matches: List<PVApiMatch>)

data class PVApiMatch(
    val oid: Oid<PVDocument>,
    @JsonProperty("luontikohdeluokan-oid") val assignmentOid: Oid<PVAssignment>,
)
data class PVApiLatestVersion(
    @JsonProperty("versio") val version: PVId,
    @JsonProperty("nimi") val name: FileName,
    @JsonProperty("muokattu") val changeTime: Instant
)

data class PVApiFileMetadata(
    @JsonProperty("tila") val materialState: PVDictionaryCode,
    @JsonProperty("kuvaus") val description: FreeText?,
    @JsonProperty("laji") val materialCategory: PVDictionaryCode?,
    @JsonProperty("dokumenttityyppi") val documentType: PVDictionaryCode,
    @JsonProperty("ryhma") val materialGroup: PVDictionaryCode,
    @JsonProperty("tekniikka-alat") val technicalFields: List<PVDictionaryCode>,
    @JsonProperty("sisaltaa-henkilotietoja") val containsPersonalInfo: Boolean?
)

data class PVApiFile(
    @JsonProperty("tuorein-versio") val latestVersion: PVApiLatestVersion,
    @JsonProperty("metatiedot") val metadata: PVApiFileMetadata
)

data class PVApiRedirect(
    @JsonProperty("master-jarjestelma") val masterSystem: PVMasterSystem,
    @JsonProperty("kohdeluokka") val targetCategory: PVTargetCategory,
    @JsonProperty("kohde-url") val targetUrl: HttpsUrl,
)

data class PVApiProperties(
    @JsonProperty("nimi") val name: PVDictionaryName,
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

data class PVSearch(
    val id: IntId<PVSearch>,
    val token: PVId,
    val state: PVFetchStatus,
    val validUntil: Instant,
)

enum class PVFetchStatus {
    WAITING,
    FETCHING,
    FINISHED,
    ERROR,
}


val pvBearerTokenLength = 1..5000
data class PVBearerToken @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<PVBearerToken>, CharSequence by value {
    init {
        assertLength<PVBearerToken>(value, pvBearerTokenLength)
        JWT.decode(value)
    }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: PVBearerToken): Int = value.compareTo(other.value)
}

enum class BearerTokenType { Bearer }
data class PVAccessToken(
    @JsonProperty("access_token") val accessToken: PVBearerToken,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: BearerTokenType,
)
