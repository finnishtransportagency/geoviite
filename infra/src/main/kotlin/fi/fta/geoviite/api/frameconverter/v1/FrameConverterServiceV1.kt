package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometryPoint
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.InputValidationException
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
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
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

    fun coordinateToTrackAddress(
        layoutContext: LayoutContext,
        request: ValidCoordinateToTrackMeterRequestV1,
    ): List<GeoJsonFeature> {
        val searchPoint = Point(request.x, request.y)
        val nearbyLocationTracks = locationTrackService.listNearWithAlignments(
            layoutContext = layoutContext,
            bbox = boundingBoxAroundPoint(searchPoint, request.searchRadius) // TODO GVT-2687 Radius != Bounding box
        )

        val filteredLocationTracks = nearbyLocationTracks
            .filter { (locationTrack, _) -> filterByLocationTrackName(request.locationTrackName, locationTrack) }
            .filter { (locationTrack, _) -> filterByLocationTrackType(request.locationTrackType, locationTrack) }
            .filter { (locationTrack, _) -> filterByTrackNumber(layoutContext, request.trackNumberName, locationTrack) }

        val nearestMatch = findNearestLocationTrack(searchPoint, filteredLocationTracks)
            ?: return createErrorResponse(request.identifier, "features-not-found")

        val (trackNumber, geocodedAddress) = geocodingService.getGeocodingContext(
            layoutContext = layoutContext,
            trackNumberId = nearestMatch.locationTrack.trackNumberId,
        ).let { geocodingContext ->
            geocodingContext?.trackNumber to geocodingContext?.getAddressAndM(searchPoint)
        }

        if (trackNumber == null || geocodedAddress == null) {
            return createErrorResponse(request.identifier, "address-geocoding-failed")
        }

        return createCoordinateToTrackMeterResponse(layoutContext, request, nearestMatch, trackNumber, geocodedAddress)
    }

    fun validateCoordinateToTrackMeterRequest(
        request: CoordinateToTrackMeterRequestV1
    ): Pair<ValidCoordinateToTrackMeterRequestV1?, List<GeoJsonFeatureErrorResponseV1>> {
        val allowedSearchRadiusRange = 1.0..1000.0
        val allowedResponseSettings = setOf(1, 5, 10)

        val errors = mutableListOf(
            validate(request.x != null) { "missing-x-coordinate" },
            validate(request.y != null) { "missing-y-coordinate" },
            validate(request.searchRadius != null) { "search-radius-undefined" },
            validate(request.searchRadius != null && request.searchRadius >= allowedSearchRadiusRange.start) {
                "search-radius-under-range"
            },
            validate(request.searchRadius != null && request.searchRadius <= allowedSearchRadiusRange.endInclusive) {
                "search-radius-over-range"
            },
            validate(request.responseSettings.all { setting -> setting in allowedResponseSettings}) {
                "invalid-response-settings"
            }
        )

        val mappedLocationTrackTypeOrNull = when (request.locationTrackType) {
            null -> null

            "pääraide" -> LocationTrackType.MAIN
            "sivuraide" -> LocationTrackType.SIDE
            "kujaraide" -> LocationTrackType.CHORD
            "turvaraide" -> LocationTrackType.TRAP

            else -> {
                errors.add("invalid-location-track-type")
                null
            }
        }

        val trackNumberNameOrNull = request.trackNumberName?.let { unvalidatedTrackNumberName ->
            try {
                TrackNumber(unvalidatedTrackNumberName)
            } catch (e: InputValidationException) {
                errors.add("invalid-track-number-name")
                null
            }
        }

        val locationTrackNameOrNull = request.locationTrackName?.let { unvalidatedLocationTrackName ->
            try {
                AlignmentName(unvalidatedLocationTrackName)
            } catch (e: InputValidationException) {
                errors.add("invalid-location-track-name")
                null
            }
        }

        val nonNullErrors = errors.filterNotNull()
        return if (nonNullErrors.isEmpty()) {
            // Already checked earlier but type-inference is not smart enough =(
            requireNotNull(request.x)
            requireNotNull(request.y)
            requireNotNull(request.searchRadius)

            val validRequest = ValidCoordinateToTrackMeterRequestV1(
                request.identifier,
                request.x,
                request.y,
                request.searchRadius,
                trackNumberName = trackNumberNameOrNull,
                locationTrackName = locationTrackNameOrNull,
                locationTrackType = mappedLocationTrackTypeOrNull,
                request.responseSettings,
            )

            validRequest to emptyList()
        } else {
            val errorResponse = createErrorResponse(
                identifier = request.identifier,
                errorLocalizationKeys = nonNullErrors,
            )

            null to errorResponse
        }
    }

    fun createErrorResponse(
        identifier: String?,
        errorLocalizationKey: String
    ) = createErrorResponse(identifier, listOf(errorLocalizationKey))

    fun createErrorResponse(
        identifier: String?,
        errorLocalizationKeys: List<String>
    ): List<GeoJsonFeatureErrorResponseV1> {
        return listOf(
            GeoJsonFeatureErrorResponseV1(
                identifier = identifier,
                errorMessages = errorLocalizationKeys.joinToString(" ") { localizationKey ->
                    translation.t("integration-api.error.$localizationKey")
                }
            )
        )
    }

    private fun translateLocationTrackType(locationTrack: LocationTrack): String {
        return translation.t("enum.location-track-type.${locationTrack.type}")
    }

    private fun filterByLocationTrackType(
        locationTrackType: LocationTrackType?,
        locationTrack: LocationTrack,
    ): Boolean {
        return if (locationTrackType == null) {
            true
        } else {
            locationTrackType == locationTrack.type
        }
    }

    private fun filterByTrackNumber(
        layoutContext: LayoutContext,
        requestTrackNumber: TrackNumber?,
        locationTrack: LocationTrack,
    ): Boolean {
        return if (requestTrackNumber == null) {
            true
        } else {
            val trackNumber = trackNumberService.get(layoutContext, locationTrack.trackNumberId)
            requestTrackNumber == trackNumber?.number
        }
    }

    private fun createCoordinateToTrackMeterResponse(
        layoutContext: LayoutContext,
        request: ValidCoordinateToTrackMeterRequestV1,
        nearestMatch: NearestLocationTrackPointMatch,
        trackNumber: TrackNumber,
        geocodedAddress: AddressAndM,
    ): List<CoordinateToTrackMeterResponseV1> {

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
            createDetailedFeatureMatchData(layoutContext, nearestMatch, trackNumber, geocodedAddress)
        } else {
            null
        }

        return listOf(
            CoordinateToTrackMeterResponseV1(
                geometry = featureGeometry,
                properties = CoordinateToTrackMeterResponsePropertiesV1(
                    id = request.identifier,
                    closestLocationTrackMatch = closestLocationTrackMatch,
                    conversionDetails = conversionDetails,
                )
            )
        )
    }

    private fun createDetailedFeatureMatchData(
        layoutContext: LayoutContext,
        nearestMatch: NearestLocationTrackPointMatch,
        trackNumber: TrackNumber,
        geocodedAddress: AddressAndM,
    ): CoordinateToTrackMeterConversionDetailsV1 {
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
            kmNumber = geocodedAddress.address.kmNumber.number,
            trackMeter = trackMeter,
            trackMeterDecimals = trackMeterDecimals,
        )
    }
}

private fun findNearestLocationTrack(
    searchPoint: Point,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): NearestLocationTrackPointMatch? {
    return locationTracks.map { (locationTrack, alignment) ->
        val closestPointOnTrack = alignment.getClosestPoint(searchPoint)?.first

        closestPointOnTrack?.let {
            NearestLocationTrackPointMatch(
                locationTrack,
                alignment,
                closestPointOnTrack,
                distanceToClosestPoint = lineLength(searchPoint, closestPointOnTrack),
            )
        }
    }.minByOrNull { locationTrackPointMatch ->
        locationTrackPointMatch?.distanceToClosestPoint
            ?: Double.POSITIVE_INFINITY
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
    locationTrackName: AlignmentName?,
    locationTrack: LocationTrack,
): Boolean {
    return if (locationTrackName == null) {
        true
    } else {
        locationTrackName == locationTrack.name
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

private fun validate(valid: Boolean, issue: () -> String): String? {
    return if (valid) {
        null
    } else {
        issue()
    }
}
