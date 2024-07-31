package fi.fta.geoviite.api.frameconverter.v1

import ClosestLocationTrackMatchV1
import CoordinateToTrackMeterConversionDetailsV1
import CoordinateToTrackMeterRequestV1
import CoordinateToTrackMeterResponsePropertiesV1
import CoordinateToTrackMeterResponseV1
import GeoJsonFeatureErrorResponseV1
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometryPoint
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.geocoding.AddressAndM
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
    localizationService: LocalizationService,
) {
    val translation = localizationService.getLocalization(LocalizationLanguage.FI)

    fun createNoFeaturesFoundError(identifier: String?) = GeoJsonFeatureErrorResponseV1(
        identifier = identifier,
        errorMessages = translation.t("integration-api.error.features-not-found")
    )

    fun createMissingXCoordinateError(identifier: String?) = GeoJsonFeatureErrorResponseV1(
        identifier = identifier,
        errorMessages = translation.t("integration-api.error.missing-x-coordinate")
    )

    fun createMissingYCoordinateError(identifier: String?) = GeoJsonFeatureErrorResponseV1(
        identifier = identifier,
        errorMessages = translation.t("integration-api.error.missing-y-coordinate")
    )

    fun coordinateToTrackAddress(
        layoutContext: LayoutContext,
        request: CoordinateToTrackMeterRequestV1,
    ): List<GeoJsonFeature> {
        if (request.x == null) {
            return listOf(createMissingXCoordinateError(request.identifier))
        }

        if (request.y == null) {
            return listOf(createMissingYCoordinateError(request.identifier))
        }

        val searchPoint = Point(request.x, request.y)
        val nearbyLocationTracks = locationTrackService.listNearWithAlignments(
            layoutContext = layoutContext,
            bbox = boundingBoxAroundPoint(searchPoint, request.searchRadius) // TODO Radius != bounding box
        )

        val filteredLocationTracks = nearbyLocationTracks
            .filter { (locationTrack, _) -> filterByLocationTrackName(request.locationTrackName, locationTrack) }
            .filter { (locationTrack, _) -> filterByLocationTrackType(request.locationTrackType, locationTrack) }
            .filter { (locationTrack, _) -> filterByTrackNumber(layoutContext, request.trackNumberName, locationTrack) }

        val nearestMatch = findNearestLocationTrack(searchPoint, filteredLocationTracks)
            ?: return listOf(createNoFeaturesFoundError(request.identifier))

        val geocodedAddress = geocodingService.getGeocodingContext(
            layoutContext = layoutContext,
            trackNumberId = nearestMatch.locationTrack.trackNumberId,
        )?.getAddressAndM(searchPoint)

        requireNotNull(geocodedAddress) // TODO Improve error handling

        // TODO Does this sometimes actually return multiple values?
        return listOf(
            createCoordinateToTrackMeterResponse(layoutContext, request, nearestMatch, geocodedAddress),
        )
    }

    private fun filterByLocationTrackType(
        locationTrackTypeTranslation: String?,
        locationTrack: LocationTrack,
    ): Boolean {
        return if (locationTrackTypeTranslation == null) {
            true
        } else {
            locationTrackTypeTranslation == translateLocationTrackType(locationTrack).lowercase()
        }
    }

    private fun filterByTrackNumber(
        layoutContext: LayoutContext,
        trackNumberName: String?,
        locationTrack: LocationTrack,
    ): Boolean {
        return if (trackNumberName == null) {
            true
        } else {
            val trackNumber = trackNumberService.get(layoutContext, locationTrack.trackNumberId)

            // TODO Improve error handling (trackNumber somehow not found)?
            // Also are there some performance considerations? (DB query for each check at least for now?)
            trackNumberName == trackNumber?.number.toString()
        }
    }

    private fun createCoordinateToTrackMeterResponse(
        layoutContext: LayoutContext,
        request: CoordinateToTrackMeterRequestV1,
        nearestMatch: NearestLocationTrackPointMatch,
        geocodedAddress: AddressAndM,
    ): CoordinateToTrackMeterResponseV1 {

        val featureGeometry = if (5 in request.responseSettings) {
            createFeatureGeometry(nearestMatch)
        } else {
            GeoJsonGeometryPoint.empty()
        }

        val closestLocationTrackMatch = if (1 in request.responseSettings) {
            createBasicFeatureMatchData(nearestMatch)
        } else {
            null
        }

        val conversionDetails = if (10 in request.responseSettings) {
            createDetailedFeatureMatchData(
                layoutContext,
                nearestMatch,
                geocodedAddress,
            )
        } else {
            null
        }

        return CoordinateToTrackMeterResponseV1(
            geometry = featureGeometry,
            properties = CoordinateToTrackMeterResponsePropertiesV1(
                id = request.identifier,
                closestLocationTrackMatch = closestLocationTrackMatch,
                conversionDetails = conversionDetails,
            )
        )
    }

    private fun createDetailedFeatureMatchData(
        layoutContext: LayoutContext,
        nearestMatch: NearestLocationTrackPointMatch,
        geocodedAddress: AddressAndM,
    ): CoordinateToTrackMeterConversionDetailsV1 {
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

        val (trackMeter, trackMeterDecimals) = splitBigDecimal(geocodedAddress.address.meters)
        val translatedLocationTrackType = translateLocationTrackType(nearestMatch.locationTrack).lowercase()

        return CoordinateToTrackMeterConversionDetailsV1(
            trackNumber = trackNumber,
            locationTrackName = nearestMatch.locationTrack.name,
            locationTrackDescription = locationTrackDescription,
            translatedLocationTrackType = translatedLocationTrackType,
            kmNumber = geocodedAddress.address.kmNumber,
            trackMeter = trackMeter,
            trackMeterDecimals = trackMeterDecimals,
        )
    }

    private fun translateLocationTrackType(locationTrack: LocationTrack): String {
        return translation.t("enum.location-track-type.${locationTrack.type}")
    }
}

private fun findNearestLocationTrack(
    searchPoint: Point,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): NearestLocationTrackPointMatch? {
    return locationTracks.map { (locationTrack, alignment) ->
        val closestPointOnTrack = alignment.getClosestPoint(searchPoint)?.first
        requireNotNull(closestPointOnTrack) // TODO Better error

        NearestLocationTrackPointMatch(
            locationTrack,
            alignment,
            closestPointOnTrack,
            distanceToClosestPoint = lineLength(searchPoint, closestPointOnTrack),
        )
    }.minByOrNull { locationTrackPointMatch ->
        locationTrackPointMatch.distanceToClosestPoint
    }
}

private fun createFeatureGeometry(
    nearestMatch: NearestLocationTrackPointMatch,
): GeoJsonGeometryPoint {
    return GeoJsonGeometryPoint(
        coordinates = listOf(
            nearestMatch.closestPointOnTrack.x,
            nearestMatch.closestPointOnTrack.y,
        )
    )
}

private fun createBasicFeatureMatchData(
    nearestMatch: NearestLocationTrackPointMatch,
): ClosestLocationTrackMatchV1 {
    return ClosestLocationTrackMatchV1(
        x = nearestMatch.closestPointOnTrack.x,
        y = nearestMatch.closestPointOnTrack.y,
        distanceFromRequestPoint = nearestMatch.distanceToClosestPoint,
    )
}

private fun filterByLocationTrackName(
    locationTrackName: String?,
    locationTrack: LocationTrack,
): Boolean {
    return if (locationTrackName == null) {
        true
    } else {
        locationTrackName == locationTrack.name.toString()
    }
}

private fun splitBigDecimal(number: BigDecimal, decimalPlaces: Int = 3): Pair<Int, Int> {
    val wholePart = number.toBigInteger().toInt()
    val fractionalPart = number.subtract(BigDecimal(wholePart))
    val scaledFractionalPart = fractionalPart
        .setScale(decimalPlaces, RoundingMode.DOWN)
        .movePointRight(decimalPlaces).toInt()

    return Pair(wholePart, scaledFractionalPart)
}
