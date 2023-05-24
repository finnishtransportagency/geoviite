package fi.fta.geoviite.infra.projektivelho

import PVAssignment
import PVCode
import PVDocument
import PVId
import PVName
import PVProject
import PVProjectGroup
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import java.time.Instant

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

data class PVApiSearchStatus(
    @JsonProperty("tila") val state: String,
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
    @JsonProperty("tila") val materialState: PVCode,
    @JsonProperty("kuvaus") val description: FreeText?,
    @JsonProperty("laji") val materialCategory: PVCode?,
    @JsonProperty("dokumenttityyppi") val documentType: PVCode,
    @JsonProperty("ryhma") val materialGroup: PVCode,
    @JsonProperty("tekniikka-alat") val technicalFields: List<PVCode>,
    @JsonProperty("sisaltaa-henkilotietoja") val containsPersonalInfo: Boolean?
)

data class PVApiFile(
    @JsonProperty("tuorein-versio") val latestVersion: PVApiLatestVersion,
    @JsonProperty("metatiedot") val metadata: PVApiFileMetadata
)

data class PVApiRedirect(
    @JsonProperty("master-jarjestelma") val masterSystem: String,
    @JsonProperty("kohdeluokka") val targetCategory: String,
    @JsonProperty("kohde-url") val targetUrl: String,
)

data class PVApiProperties(
    @JsonProperty("nimi") val name: PVName,
    @JsonProperty("tila") val state: PVCode,
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

const val PROJEKTIVELHO_SEARCH_STATE_READY = "valmis"

data class PVAccessToken(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: String,
)
