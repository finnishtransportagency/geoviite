package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.math.BoundingBox
import java.time.Instant

data class LayoutTrackNumber(
    val number: TrackNumber,
    val description: TrackNumberDescription,
    val state: LayoutState,
    val startAddress: TrackMeter,
    val boundingBox: BoundingBox? = null,
    val length: LineM<ReferenceLineM> = LineM(0.0),
    val segmentCount: Int = 0,
    @JsonIgnore override val contextData: LayoutContextData<LayoutTrackNumber>,
    //    @JsonIgnore val geometryVersion: RowVersion<ReferenceLineGeometry>? = null,
) : LayoutAsset<LayoutTrackNumber>(contextData) {
    @JsonIgnore val exists = !state.isRemoved()

    init {
        require(description.isNotBlank()) { "TrackNumber should have a non-blank description" }
        require(description.length < 100) { "TrackNumber description too long: ${description.length}>100" }
    }

    override fun toLog(): String =
        logFormat("id" to id, "version" to version, "context" to contextData::class.simpleName, "number" to number)

    override fun withContext(contextData: LayoutContextData<LayoutTrackNumber>): LayoutTrackNumber =
        copy(contextData = contextData)

    //    fun getGeometryVersionOrThrow(): RowVersion<ReferenceLineGeometry> =
    //        requireNotNull(geometryVersion) { "${this::class.simpleName} has no geometry: id=$id" }
}

data class TrackNumberAndChangeTime(val id: IntId<LayoutTrackNumber>, val number: TrackNumber, val changeTime: Instant)
