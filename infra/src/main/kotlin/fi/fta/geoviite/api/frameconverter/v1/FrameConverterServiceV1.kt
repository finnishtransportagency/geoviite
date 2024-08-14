package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometryPoint
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
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
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.produceIf
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

        val trackNumbers = request.trackNumberName?.let { trackNumberService.mapById(layoutContext) } ?: emptyMap()

        val filteredLocationTracks = nearbyLocationTracks
            .filter { (locationTrack, _) -> filterByLocationTrackName(request.locationTrackName, locationTrack) }
            .filter { (locationTrack, _) -> filterByLocationTrackType(request.locationTrackType, locationTrack) }
            .filter { (locationTrack, _) -> filterByTrackNumber(trackNumbers, request.trackNumberName, locationTrack) }

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

        val errors = mutableListOf(
            produceIf(request.x == null) { "missing-x-coordinate" },
            produceIf(request.y == null) { "missing-y-coordinate" },
            produceIf(request.searchRadius == null) { "search-radius-undefined" },
            produceIf(request.searchRadius != null && request.searchRadius < allowedSearchRadiusRange.start) {
                "search-radius-under-range"
            },
            produceIf(request.searchRadius != null && request.searchRadius > allowedSearchRadiusRange.endInclusive) {
                "search-radius-over-range"
            },
            produceIf(FrameConverterResponseSettingV1.INVALID in request.responseSettings) {
                "invalid-response-settings"
            }
        )

        val mappedLocationTrackTypeOrNull = when (request.locationTrackType) {
            null -> null

            FrameConverterLocationTrackTypeV1.MAIN -> LocationTrackType.MAIN
            FrameConverterLocationTrackTypeV1.SIDE -> LocationTrackType.SIDE
            FrameConverterLocationTrackTypeV1.CHORD -> LocationTrackType.CHORD
            FrameConverterLocationTrackTypeV1.TRAP -> LocationTrackType.TRAP

            else -> {
                errors.add("invalid-location-track-type")
                null
            }
        }

        val trackNumberNameOrNull = request.trackNumberName?.let { unvalidatedTrackNumberName ->
            try {
                TrackNumber(unvalidatedTrackNumberName.toString())
            } catch (e: InputValidationException) {
                errors.add("invalid-track-number-name")
                null
            }
        }

        val locationTrackNameOrNull = request.locationTrackName?.let { unvalidatedLocationTrackName ->
            try {
                AlignmentName(unvalidatedLocationTrackName.toString())
            } catch (e: InputValidationException) {
                errors.add("invalid-location-track-name")
                null
            }
        }

        val nonNullErrors = errors.filterNotNull()
        return if (nonNullErrors.isEmpty()) {
            val validRequest = ValidCoordinateToTrackMeterRequestV1(
                identifier = request.identifier,

                // Already checked earlier but type-inference is not smart enough =(
                x = requireNotNull(request.x),
                y = requireNotNull(request.y),
                searchRadius = requireNotNull(request.searchRadius),

                trackNumberName = trackNumberNameOrNull,
                locationTrackName = locationTrackNameOrNull,
                locationTrackType = mappedLocationTrackTypeOrNull,
                responseSettings = request.responseSettings,
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
        identifier: FrameConverterIdentifierV1?,
        errorLocalizationKey: String
    ): List<GeoJsonFeatureErrorResponseV1> = createErrorResponse(identifier, listOf(errorLocalizationKey))

    fun createErrorResponse(
        identifier: FrameConverterIdentifierV1?,
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

    private fun createCoordinateToTrackMeterResponse(
        layoutContext: LayoutContext,
        request: ValidCoordinateToTrackMeterRequestV1,
        nearestMatch: NearestLocationTrackPointMatch,
        trackNumber: TrackNumber,
        geocodedAddress: AddressAndM,
    ): List<CoordinateToTrackMeterResponseV1> {

        val featureGeometry =
            if (FrameConverterResponseSettingV1.FeatureGeometry in request.responseSettings) {
                createFeatureGeometry(nearestMatch)
            } else {
                GeoJsonGeometryPoint.empty()
            }

        val closestLocationTrackMatch =
            if (FrameConverterResponseSettingV1.BasicFeatureMatchData in request.responseSettings) {
                createBasicFeatureMatchData(nearestMatch)
            } else {
                null
            }

        val conversionDetails =
            if (FrameConverterResponseSettingV1.FeatureMatchDetails in request.responseSettings) {
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
): BasicFeatureMatchDataV1 {
    return BasicFeatureMatchDataV1(
        x = nearestMatch.closestPointOnTrack.x,
        y = nearestMatch.closestPointOnTrack.y,
        distanceFromRequestPoint = nearestMatch.distanceToClosestPoint,
    )
}

private fun filterByLocationTrackName(
    locationTrackName: AlignmentName?,
    locationTrack: LocationTrack,
): Boolean = locationTrackName == null || locationTrackName == locationTrack.name

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
    trackNumbers: Map<IntId<TrackLayoutTrackNumber>, TrackLayoutTrackNumber>,
    requestTrackNumber: TrackNumber?,
    locationTrack: LocationTrack,
): Boolean {
    return if (requestTrackNumber == null) {
        true
    } else {
        requestTrackNumber == trackNumbers[locationTrack.trackNumberId]?.number
    }
}

private fun splitBigDecimal(number: BigDecimal, decimalPlaces: Int = 3): Pair<Int, Int> {
    val wholePart = number.toBigInteger().toInt()
    val fractionalPart = number.subtract(BigDecimal(wholePart))
    val scaledFractionalPart = fractionalPart
        .setScale(decimalPlaces, RoundingMode.DOWN)
        .movePointRight(decimalPlaces)
        .toInt()

    return Pair(wholePart, scaledFractionalPart)
}
