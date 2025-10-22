package fi.fta.geoviite.infra.ratko.model

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber

enum class OperationalPointRaideType {
    LP, // Liikennepaikka
    LPO, // Liikennepaikan osa
    OLP, // Osiin jaettu liikennepaikka
    SEIS, // Seisake
    LVH,
    // Linjavaihde
}

abstract class AbstractRatkoOperationalPoint(
    open val name: String,
    open val abbreviation: String,
    open val uicCode: String?,
    open val type: OperationalPointRaideType,
    open val location: Point,
)

data class RatkoOperationalPointParse(
    val externalId: Oid<RatkoOperationalPoint>,
    override val name: String,
    override val abbreviation: String,
    override val uicCode: String?,
    override val type: OperationalPointRaideType,
    override val location: Point,
    val trackNumberExternalId: Oid<RatkoRouteNumber>,
) : AbstractRatkoOperationalPoint(name, abbreviation, uicCode, type, location)

data class RatkoOperationalPoint(
    val externalId: Oid<RatkoOperationalPoint>,
    override val name: String,
    override val abbreviation: String,
    override val uicCode: String?,
    override val type: OperationalPointRaideType,
    override val location: Point,
    val trackNumberId: IntId<LayoutTrackNumber>,
) : AbstractRatkoOperationalPoint(name, abbreviation, uicCode, type, location)

fun parseAsset(asset: RatkoOperationalPointAsset): RatkoOperationalPointParse? {
    val externalId = Oid<RatkoOperationalPoint>(asset.id)
    val type = asset.getEnumProperty<OperationalPointRaideType>("operational_point_type")
    val soloPoint =
        asset.locations
            ?.get(0)
            ?.nodecollection
            ?.nodes
            ?.find { node -> node.nodeType == RatkoNodeType.SOLO_POINT }
            ?.point
    val location = soloPoint?.geometry?.coordinates?.let { cs -> Point(cs[0], cs[1]) }
    val trackNumberExternalId: Oid<RatkoRouteNumber>? = soloPoint?.routenumber?.toString()?.let(::Oid)
    return if (type == null || location == null || trackNumberExternalId == null) null
    else
        RatkoOperationalPointParse(
            externalId = externalId,
            name = asset.getStringProperty("name") ?: "",
            abbreviation = asset.getStringProperty("operational_point_abbreviation") ?: "",
            uicCode = asset.getIntProperty("operational_point_code")?.toString(),
            type = type,
            location = location,
            trackNumberExternalId = trackNumberExternalId,
        )
}
