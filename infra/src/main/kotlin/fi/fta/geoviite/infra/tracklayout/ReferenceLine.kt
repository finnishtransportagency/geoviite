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
    @JsonIgnore val alignmentVersion: RowVersion<LayoutAlignment>? = null,
) : PolyLineLayoutAsset<ReferenceLine>(contextData) {

    init {
        require(dataType == DataType.TEMP || alignmentVersion != null) { "ReferenceLine in DB must have an alignment" }
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
            "alignment" to alignmentVersion,
        )

    fun getAlignmentVersionOrThrow(): RowVersion<LayoutAlignment> =
        requireNotNull(alignmentVersion) { "${this::class.simpleName} has no an alignment: id=$id" }

    override fun withContext(contextData: LayoutContextData<ReferenceLine>): ReferenceLine =
        copy(contextData = contextData)
}
