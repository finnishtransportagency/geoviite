package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometryPoint
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.geocoding.AddressAndM
import fi.fta.geoviite.infra.geocoding.AddressPoint
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

    fun trackAddressToCoordinate(
        layoutContext: LayoutContext,
        request: ValidTrackAddressToCoordinateRequestV1,
    ): List<GeoJsonFeature> {

        val tracksAndAlignments = locationTrackService.listWithAlignments(
            layoutContext = layoutContext,
            trackNumberId = request.trackNumber.id as IntId,
        )
            .filter { (locationTrack, _) -> filterByLocationTrackName(request.locationTrackName, locationTrack) }
            .filter { (locationTrack, _) -> filterByLocationTrackType(request.locationTrackType, locationTrack) }

        val geocodingContext = geocodingService.getGeocodingContext(
            layoutContext = layoutContext,
            trackNumberId = request.trackNumber.id,
        )

        geocodingContext ?: return createErrorResponse(request.identifier, "address-geocoding-failed")

        val tracksAndMatchingAddressPoints = tracksAndAlignments
            .map { (locationTrack, alignment) ->
                locationTrack to geocodingContext.getTrackLocation(
                    alignment = alignment,
                    address = request.trackAddress,
                )
            }
            .filter { (_, addressPoint) -> addressPoint != null }

        return tracksAndMatchingAddressPoints.map { (locationTrack, addressPoint) ->
            createTrackMeterToCoordinateResponse(layoutContext, request, locationTrack, requireNotNull(addressPoint))
        }.ifEmpty {
            createErrorResponse(request.identifier, "features-not-found")
        }
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

        val mappedLocationTrackTypeOrNull = mapLocationTrackTypeToDomainTypeOrNull(request.locationTrackType)
            .let { (mappedType, errorKey) ->
                errorKey?.let { errors.add(errorKey) }
                mappedType
            }

        val trackNumberNameOrNull = createValidTrackNumberNameOrNull(request.trackNumberName)
            .let { (trackNumberOrNull, errorKey) ->
                errorKey?.let { errors.add(errorKey) }
                trackNumberOrNull
            }

        val locationTrackNameOrNull = createValidAlignmentNameOrNull(request.locationTrackName)
            .let { (trackNameOrNull, errorKey) ->
                errorKey?.let { errors.add(errorKey) }
                trackNameOrNull
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

    fun validateTrackMeterToCoordinateRequest(
        request: TrackAddressToCoordinateRequestV1,
    ): Pair<ValidTrackAddressToCoordinateRequestV1?, List<GeoJsonFeatureErrorResponseV1>> {
        val validKilometerRange = 0..9999
        val validMeterRange = 0..9999

        val errors = mutableListOf(
            produceIf(request.trackNumberName == null) { "missing-track-number" },
            produceIf(request.trackKilometer == null) { "missing-track-kilometer" },
            produceIf(request.trackMeter == null) { "missing-track-meter" },

            produceIf(FrameConverterResponseSettingV1.INVALID in request.responseSettings) {
                "invalid-response-settings"
            },

            request.trackKilometer?.let {
                produceIf(request.trackKilometer < validKilometerRange.start) {
                    "track-kilometer-under-range"
                }
            },

            request.trackKilometer?.let {
                produceIf(request.trackKilometer > validKilometerRange.endInclusive) {
                    "track-kilometer-over-range"
                }
            },

            request.trackMeter?.let {
                produceIf(request.trackMeter < validMeterRange.start) {
                    "track-meter-under-range"
                }
            },

            request.trackMeter?.let {
                produceIf(request.trackMeter > validMeterRange.endInclusive) {
                    "track-meter-over-range"
                }
            },
        )

        val mappedLocationTrackTypeOrNull = mapLocationTrackTypeToDomainTypeOrNull(request.locationTrackType)
            .let { (mappedTrackType, errorKey) ->
                errorKey?.let { errors.add(errorKey) }
                mappedTrackType
            }

        val layoutTrackNumberOrNull = createValidTrackNumberNameOrNull(request.trackNumberName)
            .let { (trackNumberOrNull, errorKey) ->
                errorKey?.let { errors.add(errorKey) }
                trackNumberOrNull
            }
            ?.let { trackNumberName ->
                val tn = trackNumberService
                    .find(MainLayoutContext.official, trackNumberName)
                    .firstOrNull()

                if (tn == null) { errors.add("track-number-not-found") }
                tn
            }

        val locationTrackNameOrNull = createValidAlignmentNameOrNull(request.locationTrackName)
            .let { (trackNameOrNull, errorKey) ->
                errorKey?.let { errors.add(errorKey) }
                trackNameOrNull
            }

        val nonNullErrors = errors.filterNotNull()
        return if (nonNullErrors.isEmpty()) {
            val validRequest = ValidTrackAddressToCoordinateRequestV1(
                identifier = request.identifier,
                trackNumber = requireNotNull(layoutTrackNumberOrNull),
                trackAddress = TrackMeter(requireNotNull(request.trackKilometer), requireNotNull(request.trackMeter)),
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
        val featureGeometry = createFeatureGeometry(request.responseSettings, nearestMatch.closestPointOnTrack)

        val featureMatchDataSimple = createBasicFeatureMatchDataOrNull(
            request.responseSettings,
            nearestMatch.closestPointOnTrack,
            nearestMatch.distanceToClosestPoint,
        )

        val conversionDetails =
            if (FrameConverterResponseSettingV1.FeatureMatchDataDetails in request.responseSettings) {
                createDetailedFeatureMatchData(
                    layoutContext,
                    nearestMatch.locationTrack,
                    trackNumber,
                    geocodedAddress.address
                )
            } else {
                null
            }

        return listOf(
            CoordinateToTrackMeterResponseV1(
                geometry = featureGeometry,
                properties = CoordinateToTrackMeterResponsePropertiesV1(
                    identifier = request.identifier,
                    featureMatchDataSimple = featureMatchDataSimple,
                    featureMatchDataDetails = conversionDetails,
                )
            )
        )
    }

    private fun createTrackMeterToCoordinateResponse(
        layoutContext: LayoutContext,
        request: ValidTrackAddressToCoordinateRequestV1,
        locationTrack: LocationTrack,
        addressPoint: AddressPoint,
    ): TrackMeterToCoordinateResponseV1 {
        val featureGeometry = createFeatureGeometry(request.responseSettings, addressPoint.point)

        val featureMatchDataSimple = createBasicFeatureMatchDataOrNull(
            request.responseSettings,
            addressPoint.point,
            distanceToClosestPoint = 0.0, // The point should be directly on the track so there's no distance to it.
        )

        val conversionDetails =
            if (FrameConverterResponseSettingV1.FeatureMatchDataDetails in request.responseSettings) {
                createDetailedFeatureMatchData(
                    layoutContext,
                    locationTrack,
                    request.trackNumber.number,
                    addressPoint.address
                )
            } else {
                null
            }

        return TrackMeterToCoordinateResponseV1(
            geometry = featureGeometry,
            properties = TrackMeterToCoordinateResponsePropertiesV1(
                identifier = request.identifier,
                featureMatchDataSimple = featureMatchDataSimple,
                featureMatchDataDetails = conversionDetails,
            )
        )
    }

    private fun createDetailedFeatureMatchData(
        layoutContext: LayoutContext,
        locationTrack: LocationTrack,
        trackNumber: TrackNumber,
        trackMeter: TrackMeter,
    ): FeatureMatchDataDetailsV1 {
        val locationTrackDescription = locationTrackService.getFullDescription(
            layoutContext = layoutContext,
            locationTrack = locationTrack,
            LocalizationLanguage.FI,
        )

        val (trackMeterIntegers, trackMeterDecimals) = splitBigDecimal(trackMeter.meters)
        val translatedLocationTrackType = translateLocationTrackType(locationTrack).lowercase()

        return FeatureMatchDataDetailsV1(
            trackNumber = trackNumber,
            locationTrackName = locationTrack.name,
            locationTrackDescription = locationTrackDescription,
            translatedLocationTrackType = translatedLocationTrackType,
            kmNumber = trackMeter.kmNumber.number,
            trackMeter = trackMeterIntegers,
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
    responseSettings: FrameConverterResponseSettingsV1,
    point: AlignmentPoint,
): GeoJsonGeometryPoint {
    return if (FrameConverterResponseSettingV1.FeatureGeometryData in responseSettings) {
        GeoJsonGeometryPoint(
            coordinates = listOf(
                point.x,
                point.y,
            ),
        )
    } else {
        GeoJsonGeometryPoint.empty()
    }
}

private fun createBasicFeatureMatchDataOrNull(
    responseSettings: FrameConverterResponseSettingsV1,
    point: AlignmentPoint,
    distanceToClosestPoint: Double,
): FeatureMatchDataSimpleV1? {
    return if (FrameConverterResponseSettingV1.FeatureMatchDataSimple in responseSettings) {
        FeatureMatchDataSimpleV1(
            x = point.x,
            y = point.y,
            distanceFromRequestPoint = distanceToClosestPoint,
        )
    } else {
        null
    }
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

private fun mapLocationTrackTypeToDomainTypeOrNull(
    locationTrackType: FrameConverterLocationTrackTypeV1?,
): Pair<LocationTrackType?, String?> {
    return when (locationTrackType) {
        null -> null to null

        FrameConverterLocationTrackTypeV1.MAIN -> LocationTrackType.MAIN to null
        FrameConverterLocationTrackTypeV1.SIDE -> LocationTrackType.SIDE to null
        FrameConverterLocationTrackTypeV1.CHORD -> LocationTrackType.CHORD to null
        FrameConverterLocationTrackTypeV1.TRAP -> LocationTrackType.TRAP to null

        else -> {
            null to "invalid-location-track-type"
        }
    }
}

private fun createValidTrackNumberNameOrNull(
    unvalidatedTrackNumberName: FrameConverterStringV1?,
): Pair<TrackNumber?, String?> {
    return when (unvalidatedTrackNumberName) {
        null -> null to null
        else -> try {
            TrackNumber(unvalidatedTrackNumberName.toString()) to null
        } catch (e: InputValidationException) {
            null to "invalid-track-number-name"
        }
    }
}

private fun createValidAlignmentNameOrNull(
    unvalidatedLocationTrackName: FrameConverterStringV1?,
): Pair<AlignmentName?, String?> {
    return when (unvalidatedLocationTrackName) {
        null -> null to null
        else -> try {
            AlignmentName(unvalidatedLocationTrackName.toString()) to null
        } catch (e: InputValidationException) {
            null to "invalid-location-track-name"
        }
    }
}
