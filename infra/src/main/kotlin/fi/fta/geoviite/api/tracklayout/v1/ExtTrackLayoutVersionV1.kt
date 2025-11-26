package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationMessage
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "Rataverkon versio")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTrackLayoutVersionV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @Schema(type = "string", format = "date-time", example = "2025-12-34T12:34:56.123456Z")
    @JsonProperty(TIMESTAMP)
    val timestamp: Instant,
    @Schema(type = "string", example = "Sanallinen kuvaus version sisältämistä muutoksista")
    @JsonProperty(DESCRIPTION)
    val description: PublicationMessage,
) {
    constructor(
        publication: Publication
    ) : this(
        layoutVersion = ExtLayoutVersionV1(publication.uuid),
        timestamp = publication.publicationTime,
        description = publication.message,
    )
}

@Schema(name = "Vastaus: Rataverkon versiokokoelma")
data class ExtTrackLayoutVersionCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSIONS) val versions: List<ExtTrackLayoutVersionV1>,
)
