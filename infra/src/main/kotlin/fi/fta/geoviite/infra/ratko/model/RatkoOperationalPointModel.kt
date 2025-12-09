package fi.fta.geoviite.infra.ratko.model

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.OperationalPointAbbreviation
import fi.fta.geoviite.infra.tracklayout.OperationalPointName
import fi.fta.geoviite.infra.tracklayout.UicCode
import org.slf4j.Logger

enum class OperationalPointRaideType {
    LP, // Liikennepaikka
    LPO, // Liikennepaikan osa
    OLP, // Osiin jaettu liikennepaikka
    SEIS, // Seisake
    LVH,
    // Linjavaihde
}

abstract class AbstractRatkoOperationalPoint(
    open val name: OperationalPointName,
    open val abbreviation: OperationalPointAbbreviation,
    open val uicCode: UicCode,
    open val type: OperationalPointRaideType,
    open val location: Point,
)

data class RatkoOperationalPointParse(
    val externalId: Oid<RatkoOperationalPoint>,
    override val name: OperationalPointName,
    override val abbreviation: OperationalPointAbbreviation,
    override val uicCode: UicCode,
    override val type: OperationalPointRaideType,
    override val location: Point,
    val trackNumberExternalId: Oid<RatkoRouteNumber>,
) : AbstractRatkoOperationalPoint(name, abbreviation, uicCode, type, location)

data class RatkoOperationalPoint(
    val externalId: Oid<RatkoOperationalPoint>,
    override val name: OperationalPointName,
    override val abbreviation: OperationalPointAbbreviation,
    override val uicCode: UicCode,
    override val type: OperationalPointRaideType,
    override val location: Point,
    val trackNumberId: IntId<LayoutTrackNumber>,
) : AbstractRatkoOperationalPoint(name, abbreviation, uicCode, type, location)

fun parseAsset(asset: RatkoOperationalPointAsset, logger: Logger): RatkoOperationalPointParse? {
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
    if (location == null) {
        logger.warn("operational point with id $externalId will be rejected: null location")
    }
    val trackNumberExternalId: Oid<RatkoRouteNumber>? = soloPoint?.routenumber?.toString()?.let(::Oid)
    if (trackNumberExternalId == null) {
        logger.warn("operational point with id $externalId will be rejected: null trackNumberExternalId")
    }
    val name = getSanitizedName(asset, externalId, logger)
    val abbreviation = getSanitizedAbbreviation(asset, externalId, logger)
    val uicCode = getSanitizedUicCode(asset, externalId, logger)

    return if (
        type == null ||
            location == null ||
            trackNumberExternalId == null ||
            name == null ||
            abbreviation == null ||
            uicCode == null
    )
        null
    else
        RatkoOperationalPointParse(
            externalId = externalId,
            name = name,
            abbreviation = abbreviation,
            uicCode = uicCode,
            type = type,
            location = location,
            trackNumberExternalId = trackNumberExternalId,
        )
}

private fun getSanitizedName(
    asset: RatkoOperationalPointAsset,
    externalId: Oid<RatkoOperationalPoint>,
    logger: Logger,
): OperationalPointName? {
    val name = asset.getStringProperty("name")
    return if (name == null) {
        logger.warn("operational point with id $externalId will be rejected: null name")
        null
    } else if (!OperationalPointName.isSanitized(name)) {
        logger.warn("operational point with id $externalId will be rejected: name not sanitized")
        null
    } else {
        OperationalPointName(name)
    }
}

private fun getSanitizedAbbreviation(
    asset: RatkoOperationalPointAsset,
    externalId: Oid<RatkoOperationalPoint>,
    logger: Logger,
): OperationalPointAbbreviation? {
    val abbreviation = asset.getStringProperty("operational_point_abbreviation")
    return if (abbreviation == null) {
        logger.warn("operational point with id $externalId will be rejected: null abbreviation")
        null
    } else if (!OperationalPointAbbreviation.isSanitized(abbreviation)) {
        logger.warn("operational point with id $externalId will be rejected: abbreviation not sanitized")
        null
    } else {
        OperationalPointAbbreviation(abbreviation)
    }
}

private fun getSanitizedUicCode(
    asset: RatkoOperationalPointAsset,
    externalId: Oid<RatkoOperationalPoint>,
    logger: Logger,
): UicCode? {
    val uicCode = asset.getIntProperty("operational_point_code")
    return if (uicCode == null) {
        logger.warn("operational point with id $externalId will be rejected: null uic_code")
        null
    } else {
        UicCode(uicCode.toString())
    }
}
