package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import java.math.BigDecimal

data class GeometryKmPost(
    @JsonIgnore val staBack: BigDecimal?,
    @JsonIgnore val staAhead: BigDecimal,
    @JsonIgnore val staInternal: BigDecimal,
    val kmNumber: KmNumber?,
    val description: PlanElementName,
    val state: PlanState?,
    val location: Point?,
    val trackNumberId: IntId<TrackLayoutTrackNumber>?,
    val id: DomainId<GeometryKmPost> = StringId(),
)
