package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM

internal fun toExtAddressPoint(
    point: IPoint,
    geocodingContext: GeocodingContext<ReferenceLineM>?,
    targetCoordinateSystem: Srid,
): ExtAddressPointV1 {
    val address =
        geocodingContext?.getAddress(point)?.let { (address, intersect) ->
            if (intersect == IntersectType.WITHIN) address else null
        }
    return toExtAddressPoint(point, address, targetCoordinateSystem)
}

fun toExtAddressPoint(addressPoint: AddressPoint<*>, targetCoordinateSystem: Srid): ExtAddressPointV1 =
    toExtAddressPoint(addressPoint.point, addressPoint.address, targetCoordinateSystem)

fun toExtAddressPoint(point: IPoint, address: TrackMeter?, targetCoordinateSystem: Srid): ExtAddressPointV1 {
    val point =
        when (targetCoordinateSystem) {
            LAYOUT_SRID -> point
            else -> transformNonKKJCoordinate(LAYOUT_SRID, targetCoordinateSystem, point)
        }
    return ExtAddressPointV1(point.x, point.y, address?.formatFixedDecimals(3))
}

fun toExtCoordinate(point: IPoint, targetCoordinateSystem: Srid): ExtCoordinateV1 =
    ExtCoordinateV1(
        when (targetCoordinateSystem) {
            LAYOUT_SRID -> point
            else -> transformNonKKJCoordinate(LAYOUT_SRID, targetCoordinateSystem, point)
        }
    )
