package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationMessage
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(title = "Rataverkon versio")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExtTrackLayoutVersionV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @Schema(type = "string", format = "date-time", example = "2025-12-34T12:34:56.123456Z")
    @JsonProperty(TIMESTAMP)
    val timestamp: Instant,
    @Schema(type = "string", example = "Sanallinen kuvaus version sisältämistä muutoksista")
    @JsonProperty(DESCRIPTION)
    val description: PublicationMessage,
    @Schema(type = "string", description = "Suunnitelman tunniste. Annettu, jos versio on suunnitelman versio.")
    @JsonProperty(DESIGN_OID)
    val designOid: ExtOidV1<LayoutDesign>?,
) {
    private constructor(
        publication: Publication,
        design: LayoutDesign?,
    ) : this(
        layoutVersion = ExtLayoutVersionV1(publication.uuid),
        timestamp = publication.publicationTime,
        description = publication.message,
        designOid = design?.externalId?.let(::ExtOidV1),
    )

    companion object {
        fun of(publication: Publication, design: LayoutDesign?): ExtTrackLayoutVersionV1 {
            require(publication.layoutBranch.branch.designId == design?.id) {
                "publication design ID ${publication.layoutBranch.branch.designId} must be same as design id ${design?.id}"
            }
            return ExtTrackLayoutVersionV1(publication, design)
        }
    }
}

@Schema(title = "Vastaus: Rataverkon versiokokoelma")
data class ExtTrackLayoutVersionCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSIONS) val versions: List<ExtTrackLayoutVersionV1>,
)
