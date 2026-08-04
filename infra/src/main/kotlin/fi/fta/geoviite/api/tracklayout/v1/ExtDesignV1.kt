package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.tracklayout.DesignState
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignName
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

const val FI_DESIGN_ACTIVE = "voimassaoleva"
const val FI_DESIGN_DELETED = "poistettu"
const val FI_DESIGN_COMPLETED = "valmistunut"

@Schema(
    title = "Suunnitelman tila",
    type = "string",
    allowableValues = [FI_DESIGN_ACTIVE, FI_DESIGN_DELETED, FI_DESIGN_COMPLETED],
)
enum class ExtDesignStateV1(val value: String) {
    ACTIVE(FI_DESIGN_ACTIVE),
    DELETED(FI_DESIGN_DELETED),
    COMPLETED(FI_DESIGN_COMPLETED);

    @JsonValue fun jsonValue() = value

    companion object {
        fun of(designState: DesignState): ExtDesignStateV1 {
            return when (designState) {
                DesignState.ACTIVE -> ACTIVE
                DesignState.DELETED -> DELETED
                DesignState.COMPLETED -> COMPLETED
            }
        }
    }
}

@Schema(title = "Suunnitelma")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtDesignV1(
    @Schema(example = "1.2.246.578.13.123.456") @JsonProperty(DESIGN_OID) val designOid: ExtOidV1<LayoutDesign>,
    @JsonProperty(DESIGN_NAME) val name: LayoutDesignName,
    @JsonProperty(DESIGN_STATE) val state: ExtDesignStateV1,
    @JsonProperty(DESIGN_ESTIMATED_COMPLETION_DATE) val estimatedCompletion: LocalDate,
) {
    companion object {
        fun of(design: LayoutDesign): ExtDesignV1 =
            ExtDesignV1(
                ExtOidV1(design.externalId),
                design.name,
                ExtDesignStateV1.of(design.designState),
                design.estimatedCompletion,
            )
    }
}

@Schema(title = "Vastaus: Suunnitelma") data class ExtDesignResponseV1(@JsonProperty(DESIGN) val design: ExtDesignV1)

@Schema(title = "Vastaus: Muutettu suunnitelma")
data class ExtModifiedDesignResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(DESIGN) val design: ExtDesignV1,
)

@Schema(title = "Vastaus: Suunnitelmakokoelma")
data class ExtDesignCollectionResponseV1(@JsonProperty(DESIGN_COLLECTION) val designCollection: List<ExtDesignV1>)

@Schema(title = "Vastaus: Muutettu suunnitelmakokoelma")
data class ExtModifiedDesignCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(DESIGN_COLLECTION) val designCollection: List<ExtDesignV1>,
)
