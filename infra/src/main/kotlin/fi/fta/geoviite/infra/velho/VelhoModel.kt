package fi.fta.geoviite.infra.velho

import VelhoAssignment
import VelhoCode
import VelhoName
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import java.time.Instant

// TODO Turn into actual data classes etc.
fun searchJson(date: Instant, minOid: String, maxCount: Int) = """
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
                    "$minOid"
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

data class SearchStatus(
    @JsonProperty("tila") val state: String,
    @JsonProperty("hakutunniste") val searchId: String,
    @JsonProperty("alkuaika") val startTime: Instant,
    @JsonProperty("hakutunniste-voimassa") val validFor: Long
)

data class SearchResult(@JsonProperty("osumat") val matches: List<Match>)
data class Match(
    @JsonProperty("luontikohdeluokan-oid") val assignmentOid: Oid<VelhoAssignment>,
    val oid: Oid<ProjektiVelhoFile>,
)
data class LatestVersion(
    @JsonProperty("versio") val version: String,
    @JsonProperty("nimi") val name: FileName,
    @JsonProperty("muokattu") val changeTime: Instant
)

data class Metadata(
    @JsonProperty("tila") val materialState: VelhoCode,
    @JsonProperty("kuvaus") val description: FreeText?,
    @JsonProperty("laji") val materialCategory: VelhoCode,
    @JsonProperty("dokumenttityyppi") val documentType: VelhoCode,
    @JsonProperty("ryhma") val materialGroup: VelhoCode,
    @JsonProperty("tekniikka-alat") val technicalGroups: List<String>?,
    @JsonProperty("sisaltaa-henkilotietoja") val containsPersonalInfo: Boolean?
)

data class File(
    @JsonProperty("tuorein-versio") val latestVersion: LatestVersion,
    @JsonProperty("metatiedot") val metadata: Metadata
)

data class Redirect(
    @JsonProperty("master-jarjestelma") val masterSystem: String,
    @JsonProperty("kohdeluokka") val targetCategory: String,
    @JsonProperty("kohde-url") val targetUrl: String,
)

data class Properties(
    @JsonProperty("nimi") val name: String,
)

data class ProjectGroup(
    @JsonProperty("ominaisuudet") val properties: Properties,
    @JsonProperty("oid") val oid: String,
)

data class Project(
    @JsonProperty("ominaisuudet") val properties: Properties,
    @JsonProperty("oid") val oid: String,
    @JsonProperty("projektijoukko") val projectGroupOid: String,
)

data class Assignment(
    @JsonProperty("ominaisuudet") val properties: Properties,
    @JsonProperty("oid") val oid: String,
    @JsonProperty("projekti") val projectOid: String,
)

data class DictionaryEntry(
    val code: VelhoCode,
    val name: VelhoName,
)

data class ProjektiVelhoFile(
    val oid: Oid<ProjektiVelhoFile>,
    val content: String?,
    val metadata: Metadata,
    val latestVersion: LatestVersion,
    val assignment: Assignment?,
    val project: Project?,
    val projectGroup: ProjectGroup?
)

data class ProjektiVelhoSearch(
    val id: IntId<ProjektiVelhoSearch>,
    val token: String,
    val state: FetchStatus,
    val validUntil: Instant
)

enum class FetchStatus {
    WAITING,
    FETCHING,
    FINISHED,
    ERROR
}

enum class FileStatus {
    NOT_IM,
    IMPORTED,
    REJECTED,
    ACCEPTED,
}

enum class VelhoDictionaryType {
    DOCUMENT_TYPE,
    MATERIAL_STATE,
    MATERIAL_CATEGORY,
    ASSET_GROUP,
}


const val PROJEKTIVELHO_SEARCH_STATE_READY = "valmis"

data class AccessTokenHolder(
    val token: String,
    val expireTime: Instant,
) {
    constructor(token: AccessToken) : this(token.accessToken, Instant.now().plusSeconds(token.expiresIn))
}

data class AccessToken(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: String,
)
