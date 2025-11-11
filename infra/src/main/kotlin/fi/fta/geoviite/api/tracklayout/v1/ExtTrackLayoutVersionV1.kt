package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationMessage
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "Ratanumeron ratakilometrit")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTrackLayoutVersionV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: Uuid<Publication>,
    @JsonProperty(TIMESTAMP) val timestamp: Instant,
    @JsonProperty(DESCRIPTION) val description: PublicationMessage,
) {
    constructor(
        publication: Publication
    ) : this(
        layoutVersion = publication.uuid,
        timestamp = publication.publicationTime,
        description = publication.message,
    )
}

@Schema(name = "Vastaus: Ratanumerokokoelman ratakilometrit")
data class ExtTrackLayoutVersionCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val trackLayoutVersionFrom: Uuid<Publication>,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val trackLayoutVersionTo: Uuid<Publication>,
    @JsonProperty(TRACK_LAYOUT_VERSIONS) val versions: List<ExtTrackLayoutVersionV1>,
)
