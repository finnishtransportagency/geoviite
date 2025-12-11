package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.math.BoundingBox

data class ReferenceLine(
    val trackNumberId: IntId<LayoutTrackNumber>,
    val startAddress: TrackMeter,
    val sourceId: IntId<GeometryAlignment>?,
    val boundingBox: BoundingBox? = null,
    val length: LineM<ReferenceLineM> = LineM(0.0),
    val segmentCount: Int = 0,
    @JsonIgnore override val contextData: LayoutContextData<ReferenceLine>,
    @JsonIgnore val geometryVersion: RowVersion<ReferenceLineGeometry>? = null,
) : PolyLineLayoutAsset<ReferenceLine>(contextData) {

    init {
        require(dataType == DataType.TEMP || geometryVersion != null) { "ReferenceLine in DB must have a geometry" }
        require(startAddress.decimalCount() == 3) {
            "ReferenceLine start addresses should be given with 3 decimal precision"
        }
    }

    override fun toLog(): String =
        logFormat(
            "id" to id,
            "version" to version,
            "context" to contextData::class.simpleName,
            "trackNumber" to trackNumberId,
            "geometry" to geometryVersion,
        )

    fun getGeometryVersionOrThrow(): RowVersion<ReferenceLineGeometry> =
        requireNotNull(geometryVersion) { "${this::class.simpleName} has no geometry: id=$id" }

    override fun withContext(contextData: LayoutContextData<ReferenceLine>): ReferenceLine =
        copy(contextData = contextData)
}
