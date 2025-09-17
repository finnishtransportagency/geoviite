package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.math.Point
import java.math.BigDecimal

data class LayoutKmPost(
    val kmNumber: KmNumber,
    val gkLocation: LayoutKmPostGkLocation?,
    val state: LayoutState,
    val trackNumberId: IntId<LayoutTrackNumber>?,
    val sourceId: DomainId<GeometryKmPost>?,
    @JsonIgnore override val contextData: LayoutContextData<LayoutKmPost>,
) : LayoutAsset<LayoutKmPost>(contextData) {
    @JsonIgnore val exists = !state.isRemoved()

    val layoutLocation = gkLocation?.let { transformNonKKJCoordinate(it.location.srid, LAYOUT_SRID, it.location) }

    fun getAsIntegral(): IntegralLayoutKmPost? =
        if (state != LayoutState.IN_USE || layoutLocation == null || trackNumberId == null) null
        else IntegralLayoutKmPost(kmNumber, layoutLocation, trackNumberId)

    override fun toLog(): String =
        logFormat(
            "id" to id,
            "version" to version,
            "context" to contextData::class.simpleName,
            "kmNumber" to kmNumber,
            "trackNumber" to trackNumberId,
        )

    override fun withContext(contextData: LayoutContextData<LayoutKmPost>): LayoutKmPost =
        copy(contextData = contextData)
}

data class IntegralLayoutKmPost(
    val kmNumber: KmNumber,
    val location: Point,
    val trackNumberId: IntId<LayoutTrackNumber>,
)

data class LayoutKmPostGkLocation(
    val location: GeometryPoint,
    val source: KmPostGkLocationSource,
    val confirmed: Boolean,
)

data class KmPostInfoboxExtras(val kmLength: Double?, val sourceGeometryPlanId: IntId<GeometryPlan>?)

enum class KmPostGkLocationSource {
    FROM_GEOMETRY,
    FROM_LAYOUT,
    MANUAL,
}

data class LayoutKmLengthDetails(
    val trackNumber: TrackNumber,
    val trackNumberOid: Oid<LayoutTrackNumber>?,
    val kmNumber: KmNumber,
    val startM: BigDecimal,
    val endM: BigDecimal,
    val layoutGeometrySource: GeometrySource,
    val layoutLocation: Point?,
    val gkLocation: LayoutKmPostGkLocation?,
    val gkLocationLinkedFromGeometry: Boolean,
) {
    val length = endM - startM
}
