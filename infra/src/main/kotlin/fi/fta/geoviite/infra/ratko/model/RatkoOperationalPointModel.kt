package fi.fta.geoviite.infra.ratko.model

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber

enum class OperationalPointType { LP, LPO, OLP, SEIS, LVH; }

abstract class AbstractRatkoOperatingPoint(
    open val name: String,
    open val abbreviation: String,
    open val uicCode: String,
    open val type: OperationalPointType,
    open val location: Point,
)

data class RatkoOperatingPointParse(
    val externalId: Oid<RatkoOperatingPoint>,
    override val name: String,
    override val abbreviation: String,
    override val uicCode: String,
    override val type: OperationalPointType,
    override val location: Point,
    val trackNumberExternalId: Oid<RatkoRouteNumber>,
) : AbstractRatkoOperatingPoint(name, abbreviation, uicCode, type, location)

data class RatkoOperatingPoint(
    val externalId: Oid<RatkoOperatingPoint>,
    override val name: String,
    override val abbreviation: String,
    override val uicCode: String,
    override val type: OperationalPointType,
    override val location: Point,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
) : AbstractRatkoOperatingPoint(name, abbreviation, uicCode, type, location)

fun parseAsset(asset: RatkoOperatingPointAsset): RatkoOperatingPointParse? {
    val externalId = Oid<RatkoOperatingPoint>(asset.id)
    val type = asset.getEnumProperty<OperationalPointType>("operational_point_type")
    val soloPoint = asset.locations?.get(0)?.nodecollection?.nodes?.find { node ->
        node.nodeType == RatkoNodeType.SOLO_POINT
    }?.point
    val location = soloPoint?.geometry?.coordinates?.let { cs -> Point(cs[0], cs[1]) }
    val trackNumberExternalId: Oid<RatkoRouteNumber>? = soloPoint?.routenumber?.toString()?.let(::Oid)
    return if (type == null || location == null || trackNumberExternalId == null) null else RatkoOperatingPointParse(
        externalId = externalId,
        name = asset.getStringProperty("name") ?: "",
        abbreviation = asset.getStringProperty("operational_point_abbreviation") ?: "",
        uicCode = asset.getStringProperty("operational_point_code") ?: "",
        type = type,
        location = location,
        trackNumberExternalId = trackNumberExternalId
    )
}

