package fi.fta.geoviite.infra.velho

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.IntId
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
data class Match(val oid: String)
data class LatestVersion(
    @JsonProperty("versio") val version: String,
    @JsonProperty("nimi") val name: String,
    @JsonProperty("muokattu") val changeTime: Instant
)

data class Metadata(
    @JsonProperty("tila") val state: String?,
    @JsonProperty("kuvaus") val description: String?,
    @JsonProperty("laji") val category: String?,
    @JsonProperty("dokumenttityyppi") val documentType: String?,
    @JsonProperty("ryhma") val group: String?,
    @JsonProperty("tekniikka-alat") val technicalGroups: List<String>?,
    @JsonProperty("sisaltaa-henkilotietoja") val containsPersonalInfo: Boolean?
)

data class File(
    @JsonProperty("tuorein-versio") val latestVersion: LatestVersion,
    @JsonProperty("metatiedot") val metadata: Metadata
)

data class ProjektiVelhoFile(
    val oid: String,
    val content: String,
    val metadata: Metadata,
    val latestVersion: LatestVersion
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

const val PROJEKTIVELHO_SEARCH_STATE_READY = "valmis"

data class AccessTokenHolder(
    val token: String,
    val expireTime: Instant,
)

data class AccessToken(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: String,
)
