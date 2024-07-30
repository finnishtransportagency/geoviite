package fi.fta.geoviite.api.frameconverter.v1

import ClosestLocationTrackMatchV1
import CoordinateToTrackMeterConversionDetailsV1
import CoordinateToTrackMeterRequestV1
import CoordinateToTrackMeterResponsePropertiesV1
import CoordinateToTrackMeterResponseV1
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometryPoint
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.math.RoundingMode

private data class NearestLocationTrackPointMatch(
    val locationTrack: LocationTrack,
    val alignment: LayoutAlignment,
    val closestPointOnTrack: AlignmentPoint,
    val distanceToClosestPoint: Double,
)

@GeoviiteService
class FrameConverterServiceV1 @Autowired constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    private val localizationService: LocalizationService,
) {

    fun coordinateToTrackAddress(
        layoutContext: LayoutContext,
        request: CoordinateToTrackMeterRequestV1,
    ): GeoJsonFeature { // TODO This should actually return a LIST of features
//        val searchPoint = Point(386247.0, 6677722.0)
//        val searchPoint = Point(386277.0, 6677683.573)
//        val searchPoint = Point(428339.0, 7210151.0)

        requireNotNull(request.x)
        requireNotNull(request.y)

        val searchPoint = Point(request.x, request.y)

        val nearbyLocationTracks = locationTrackService.listNearWithAlignments(
            layoutContext = layoutContext,
            bbox = boundingBoxAroundPoint(searchPoint, 100.0) // TODO Use request
        )

        val nearestMatch = nearbyLocationTracks
            .map { (locationTrack, alignment) ->
                val closestPointOnTrack = alignment.getClosestPoint(searchPoint)?.first
                requireNotNull(closestPointOnTrack) // TODO Better error

                NearestLocationTrackPointMatch(
                    locationTrack,
                    alignment,
                    closestPointOnTrack,
                    distanceToClosestPoint = lineLength(searchPoint, closestPointOnTrack),
                )
            }
            .minByOrNull { locationTrackPointMatch -> locationTrackPointMatch.distanceToClosestPoint }

        requireNotNull(nearestMatch) // TODO Error handling

        val geocodingContext = geocodingService.getGeocodingContext(
            layoutContext = layoutContext,
            trackNumberId = nearestMatch.locationTrack.trackNumberId,
        )
        requireNotNull(geocodingContext) // TODO Error handling

        val geocodingResult = geocodingContext.getAddressAndM(searchPoint)
        requireNotNull(geocodingResult) // TODO Error handling

        val featureGeometry =
            if (5 in request.responseSettings) {
                GeoJsonGeometryPoint(
                    coordinates = listOf(
                        nearestMatch.closestPointOnTrack.x,
                        nearestMatch.closestPointOnTrack.y,
                    )
                )
            } else {
                GeoJsonGeometryPoint.empty()
            }

        val closestLocationTrackMatch =
            if (1 in request.responseSettings) {
                ClosestLocationTrackMatchV1(
                    x = nearestMatch.closestPointOnTrack.x,
                    y = nearestMatch.closestPointOnTrack.y,
                    distanceFromRequestPoint = nearestMatch.distanceToClosestPoint,
                )
            } else {
                null
            }

        val conversionDetails =
            if (10 in request.responseSettings) {
                val trackNumber = trackNumberService.get(
                    layoutContext,
                    nearestMatch.locationTrack.trackNumberId,
                )?.number

                requireNotNull(trackNumber) // TODO Error handling

                val locationTrackDescription = locationTrackService.getFullDescription(
                    layoutContext = layoutContext,
                    locationTrack = nearestMatch.locationTrack,
                    LocalizationLanguage.FI,
                )

                val (trackMeter, trackMeterDecimals) = splitBigDecimal(geocodingResult.address.meters)

                val translatedLocationTrackType = localizationService
                    .getLocalization(LocalizationLanguage.FI)
                    .t("enum.location-track-type.${nearestMatch.locationTrack.type}")
                    .lowercase()

                CoordinateToTrackMeterConversionDetailsV1(
                    trackNumber = trackNumber,
                    locationTrackName = nearestMatch.locationTrack.name,
                    locationTrackDescription = locationTrackDescription,
                    translatedLocationTrackType = translatedLocationTrackType,
                    kmNumber = geocodingResult.address.kmNumber,
                    trackMeter = trackMeter,
                    trackMeterDecimals = trackMeterDecimals,
                )
            } else {
                null
            }

        return CoordinateToTrackMeterResponseV1(
            geometry = featureGeometry,
            properties = CoordinateToTrackMeterResponsePropertiesV1(
                id = request.id,
                closestLocationTrackMatch = closestLocationTrackMatch,
                conversionDetails = conversionDetails,
            )
        )
    }
}

fun splitBigDecimal(number: BigDecimal, decimalPlaces: Int = 3): Pair<Int, Int> {
    val wholePart = number.toBigInteger().toInt()
    val fractionalPart = number.subtract(BigDecimal(wholePart))
    val scaledFractionalPart = fractionalPart
        .setScale(decimalPlaces, RoundingMode.DOWN)
        .movePointRight(decimalPlaces).toInt()

    return Pair(wholePart, scaledFractionalPart)
}
