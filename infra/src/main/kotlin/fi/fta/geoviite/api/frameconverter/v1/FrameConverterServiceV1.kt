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
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.alsoIfNull
import fi.fta.geoviite.infra.util.produceIf
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.beans.factory.annotation.Autowired

private data class NearestLocationTrackPointMatch(
    val locationTrack: LocationTrack,
    val alignment: LayoutAlignment,
    val closestPointOnTrack: AlignmentPoint,
    val distanceToClosestPoint: Double,
)

@GeoviiteService
class FrameConverterServiceV1
@Autowired
constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    localizationService: LocalizationService,
) {
    val translation = localizationService.getLocalization(LocalizationLanguage.FI)

    fun coordinateToTrackAddress(
        layoutContext: LayoutContext,
        request: ValidCoordinateToTrackAddressRequestV1,
    ): List<GeoJsonFeature> {
        val searchPoint = Point(request.x, request.y)
        val nearbyLocationTracks =
            locationTrackService.listAroundWithAlignments(
                layoutContext = layoutContext,
                point = searchPoint,
                maxDistance = request.searchRadius,
            )

        val trackNumbers = request.trackNumberName?.let { trackNumberService.mapById(layoutContext) } ?: emptyMap()

        val filteredLocationTracks =
            nearbyLocationTracks
                .filter { (locationTrack, _) -> filterByLocationTrackName(request.locationTrackName, locationTrack) }
                .filter { (locationTrack, _) -> filterByLocationTrackType(request.locationTrackType, locationTrack) }
                .filter { (locationTrack, _) ->
                    filterByTrackNumber(trackNumbers, request.trackNumberName, locationTrack)
                }

        val nearestMatch =
            findNearestLocationTrack(searchPoint, filteredLocationTracks)
                ?: return createErrorResponse(request.identifier, FrameConverterErrorV1.FeaturesNotFound)

        val (trackNumber, geocodedAddress) =
            geocodingService
                .getGeocodingContext(
                    layoutContext = layoutContext,
                    trackNumberId = nearestMatch.locationTrack.trackNumberId,
                )
                .let { geocodingContext ->
                    geocodingContext?.trackNumber to geocodingContext?.getAddressAndM(searchPoint)
                }

        return if (trackNumber == null || geocodedAddress == null) {
            createErrorResponse(request.identifier, FrameConverterErrorV1.AddressGeocodingFailed)
        } else {
            createCoordinateToTrackAddressResponse(layoutContext, request, nearestMatch, trackNumber, geocodedAddress)
        }
    }

    fun trackAddressToCoordinate(
        layoutContext: LayoutContext,
        request: ValidTrackAddressToCoordinateRequestV1,
    ): List<GeoJsonFeature> {

        val tracksAndAlignments =
            locationTrackService
                .listWithAlignments(layoutContext = layoutContext, trackNumberId = request.trackNumber.id as IntId)
                .filter { (locationTrack, _) -> filterByLocationTrackName(request.locationTrackName, locationTrack) }
                .filter { (locationTrack, _) -> filterByLocationTrackType(request.locationTrackType, locationTrack) }

        val geocodingContext =
            geocodingService.getGeocodingContext(layoutContext = layoutContext, trackNumberId = request.trackNumber.id)

        geocodingContext ?: return createErrorResponse(request.identifier, FrameConverterErrorV1.AddressGeocodingFailed)

        val tracksAndMatchingAddressPoints =
            tracksAndAlignments
                .map { (locationTrack, alignment) ->
                    locationTrack to
                        geocodingContext.getTrackLocation(alignment = alignment, address = request.trackAddress)
                }
                .filter { (_, addressPoint) -> addressPoint != null }

        return tracksAndMatchingAddressPoints
            .map { (locationTrack, addressPoint) ->
                createTrackAddressToCoordinateResponse(
                    layoutContext,
                    request,
                    locationTrack,
                    requireNotNull(addressPoint),
                )
            }
            .ifEmpty { createErrorResponse(request.identifier, FrameConverterErrorV1.FeaturesNotFound) }
    }

    fun validateCoordinateToTrackAddressRequest(
        request: CoordinateToTrackAddressRequestV1
    ): Pair<ValidCoordinateToTrackAddressRequestV1?, List<GeoJsonFeatureErrorResponseV1>> {
        val allowedSearchRadiusRange = 1.0..1000.0

        val errors =
            mutableListOf(
                produceIf(request.x == null) { FrameConverterErrorV1.MissingXCoordinate },
                produceIf(request.y == null) { FrameConverterErrorV1.MissingYCoordinate },
                produceIf(request.searchRadius == null) { FrameConverterErrorV1.SearchRadiusUndefined },
                produceIf(request.searchRadius != null && request.searchRadius < allowedSearchRadiusRange.start) {
                    FrameConverterErrorV1.SearchRadiusUnderRange
                },
                produceIf(
                    request.searchRadius != null && request.searchRadius > allowedSearchRadiusRange.endInclusive
                ) {
                    FrameConverterErrorV1.SearchRadiusOverRange
                },
                produceIf(FrameConverterResponseSettingV1.INVALID in request.responseSettings) {
                    FrameConverterErrorV1.InvalidResponseSettings
                },
            )

        val mappedLocationTrackTypeOrNull =
            mapLocationTrackTypeToDomainTypeOrNull(request.locationTrackType).let { (mappedType, errorOrNull) ->
                errorOrNull?.also(errors::add)
                mappedType
            }

        val trackNumberNameOrNull =
            createValidTrackNumberNameOrNull(request.trackNumberName).let { (trackNumberOrNull, errorOrNull) ->
                errorOrNull?.also(errors::add)
                trackNumberOrNull
            }

        val locationTrackNameOrNull =
            createValidAlignmentNameOrNull(request.locationTrackName).let { (trackNameOrNull, errorOrNull) ->
                errorOrNull?.also(errors::add)
                trackNameOrNull
            }

        val nonNullErrors = errors.filterNotNull()
        return if (nonNullErrors.isEmpty()) {
            val validRequest =
                ValidCoordinateToTrackAddressRequestV1(
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
            val errorResponse = createErrorResponse(identifier = request.identifier, errors = nonNullErrors)

            null to errorResponse
        }
    }

    fun validateTrackAddressToCoordinateRequest(
        request: TrackAddressToCoordinateRequestV1
    ): Pair<ValidTrackAddressToCoordinateRequestV1?, List<GeoJsonFeatureErrorResponseV1>> {
        val errors =
            mutableListOf(
                produceIf(request.trackNumberName == null) { FrameConverterErrorV1.MissingTrackNumber },
                produceIf(request.trackKilometer == null) { FrameConverterErrorV1.MissingTrackKilometer },
                produceIf(request.trackMeter == null) { FrameConverterErrorV1.MissingTrackMeter },
                produceIf(FrameConverterResponseSettingV1.INVALID in request.responseSettings) {
                    FrameConverterErrorV1.InvalidResponseSettings
                },
            )

        val validTrackMeterOrNull =
            when {
                request.trackKilometer != null && request.trackMeter != null ->
                    try {
                        TrackMeter(requireNotNull(request.trackKilometer), requireNotNull(request.trackMeter))
                    } catch (e: IllegalArgumentException) {
                        errors.add(FrameConverterErrorV1.InvalidTrackAddress)
                        null
                    }

                else -> null
            }

        val mappedLocationTrackTypeOrNull =
            mapLocationTrackTypeToDomainTypeOrNull(request.locationTrackType).let { (mappedTrackType, errorOrNull) ->
                errorOrNull?.also(errors::add)
                mappedTrackType
            }

        val layoutTrackNumberOrNull =
            createValidTrackNumberNameOrNull(request.trackNumberName)
                .let { (trackNumberNameOrNull, errorOrNull) ->
                    errorOrNull?.also(errors::add)
                    trackNumberNameOrNull
                }
                ?.let { trackNumberName ->
                    trackNumberService.find(MainLayoutContext.official, trackNumberName).firstOrNull().alsoIfNull {
                        errors.add(FrameConverterErrorV1.TrackNumberNotFound)
                    }
                }

        val locationTrackNameOrNull =
            createValidAlignmentNameOrNull(request.locationTrackName).let { (trackNameOrNull, errorOrNull) ->
                errorOrNull?.also(errors::add)
                trackNameOrNull
            }

        val nonNullErrors = errors.filterNotNull()
        return if (nonNullErrors.isEmpty()) {
            val validRequest =
                ValidTrackAddressToCoordinateRequestV1(
                    identifier = request.identifier,
                    trackNumber = requireNotNull(layoutTrackNumberOrNull),
                    trackAddress = requireNotNull(validTrackMeterOrNull),
                    locationTrackName = locationTrackNameOrNull,
                    locationTrackType = mappedLocationTrackTypeOrNull,
                    responseSettings = request.responseSettings,
                )

            validRequest to emptyList()
        } else {
            val errorResponse = createErrorResponse(identifier = request.identifier, errors = nonNullErrors)

            null to errorResponse
        }
    }

    fun createErrorResponse(
        identifier: FrameConverterIdentifierV1?,
        error: FrameConverterErrorV1,
    ): List<GeoJsonFeatureErrorResponseV1> = createErrorResponse(identifier, listOf(error))

    fun createErrorResponse(
        identifier: FrameConverterIdentifierV1?,
        errors: List<FrameConverterErrorV1>,
    ): List<GeoJsonFeatureErrorResponseV1> {
        return listOf(
            GeoJsonFeatureErrorResponseV1(
                identifier = identifier,
                errorMessages = errors.joinToString(" ") { error -> translation.t(error.localizationKey) },
            )
        )
    }

    private fun translateLocationTrackType(locationTrack: LocationTrack): String {
        return translation.t("enum.location-track-type.${locationTrack.type}")
    }

    private fun createCoordinateToTrackAddressResponse(
        layoutContext: LayoutContext,
        request: ValidCoordinateToTrackAddressRequestV1,
        nearestMatch: NearestLocationTrackPointMatch,
        trackNumber: TrackNumber,
        geocodedAddress: AddressAndM,
    ): List<CoordinateToTrackAddressResponseV1> {
        val featureGeometry = createFeatureGeometry(request.responseSettings, nearestMatch.closestPointOnTrack)

        val featureMatchSimple =
            createSimpleFeatureMatchOrNull(
                request.responseSettings,
                nearestMatch.closestPointOnTrack,
                nearestMatch.distanceToClosestPoint,
            )

        val conversionDetails =
            createDetailedFeatureMatchOrNull(
                request.responseSettings,
                layoutContext,
                nearestMatch.locationTrack,
                trackNumber,
                geocodedAddress.address,
            )

        return listOf(
            CoordinateToTrackAddressResponseV1(
                geometry = featureGeometry,
                properties =
                    CoordinateToTrackAddressResponsePropertiesV1(
                        identifier = request.identifier,
                        featureMatchSimple = featureMatchSimple,
                        featureMatchDetails = conversionDetails,
                    ),
            )
        )
    }

    private fun createTrackAddressToCoordinateResponse(
        layoutContext: LayoutContext,
        request: ValidTrackAddressToCoordinateRequestV1,
        locationTrack: LocationTrack,
        addressPoint: AddressPoint,
    ): TrackAddressToCoordinateResponseV1 {
        val featureGeometry = createFeatureGeometry(request.responseSettings, addressPoint.point)

        val featureMatchSimple =
            createSimpleFeatureMatchOrNull(
                request.responseSettings,
                addressPoint.point,
                distanceToClosestPoint = 0.0, // The point should be directly on the track so there's no distance to it.
            )

        val conversionDetails =
            createDetailedFeatureMatchOrNull(
                request.responseSettings,
                layoutContext,
                locationTrack,
                request.trackNumber.number,
                addressPoint.address,
            )

        return TrackAddressToCoordinateResponseV1(
            geometry = featureGeometry,
            properties =
                TrackAddressToCoordinateResponsePropertiesV1(
                    identifier = request.identifier,
                    featureMatchSimple = featureMatchSimple,
                    featureMatchDetails = conversionDetails,
                ),
        )
    }

    private fun createDetailedFeatureMatchOrNull(
        responseSettings: FrameConverterResponseSettingsV1,
        layoutContext: LayoutContext,
        locationTrack: LocationTrack,
        trackNumber: TrackNumber,
        trackMeter: TrackMeter,
    ): FeatureMatchDetailsV1? {
        return if (FrameConverterResponseSettingV1.FeatureMatchDetails in responseSettings) {
            val locationTrackDescription =
                locationTrackService.getFullDescription(
                    layoutContext = layoutContext,
                    locationTrack = locationTrack,
                    LocalizationLanguage.FI,
                )

            val (trackMeterIntegers, trackMeterDecimals) = splitBigDecimal(trackMeter.meters)
            val translatedLocationTrackType = translateLocationTrackType(locationTrack).lowercase()

            FeatureMatchDetailsV1(
                trackNumber = trackNumber,
                locationTrackName = locationTrack.name,
                locationTrackDescription = locationTrackDescription,
                translatedLocationTrackType = translatedLocationTrackType,
                kmNumber = trackMeter.kmNumber.number,
                trackMeter = trackMeterIntegers,
                trackMeterDecimals = trackMeterDecimals,
            )
        } else {
            null
        }
    }
}

private fun findNearestLocationTrack(
    searchPoint: Point,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): NearestLocationTrackPointMatch? {
    return locationTracks
        .map { (locationTrack, alignment) ->
            val closestPointOnTrack = alignment.getClosestPoint(searchPoint)?.first

            closestPointOnTrack?.let {
                NearestLocationTrackPointMatch(
                    locationTrack,
                    alignment,
                    closestPointOnTrack,
                    distanceToClosestPoint = lineLength(searchPoint, closestPointOnTrack),
                )
            }
        }
        .minByOrNull { locationTrackPointMatch ->
            locationTrackPointMatch?.distanceToClosestPoint ?: Double.POSITIVE_INFINITY
        }
}

private fun createFeatureGeometry(
    responseSettings: FrameConverterResponseSettingsV1,
    point: AlignmentPoint,
): GeoJsonGeometryPoint {
    return if (FrameConverterResponseSettingV1.FeatureGeometry in responseSettings) {
        GeoJsonGeometryPoint(coordinates = listOf(point.x, point.y))
    } else {
        GeoJsonGeometryPoint.empty()
    }
}

private fun createSimpleFeatureMatchOrNull(
    responseSettings: FrameConverterResponseSettingsV1,
    point: AlignmentPoint,
    distanceToClosestPoint: Double,
): FeatureMatchSimpleV1? {
    return if (FrameConverterResponseSettingV1.FeatureMatchSimple in responseSettings) {
        FeatureMatchSimpleV1(x = point.x, y = point.y, distanceFromRequestPoint = distanceToClosestPoint)
    } else {
        null
    }
}

private fun filterByLocationTrackName(locationTrackName: AlignmentName?, locationTrack: LocationTrack): Boolean =
    locationTrackName == null || locationTrackName == locationTrack.name

private fun filterByLocationTrackType(locationTrackType: LocationTrackType?, locationTrack: LocationTrack): Boolean {
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
    val scaledFractionalPart =
        fractionalPart.setScale(decimalPlaces, RoundingMode.DOWN).movePointRight(decimalPlaces).toInt()

    return Pair(wholePart, scaledFractionalPart)
}

private fun mapLocationTrackTypeToDomainTypeOrNull(
    locationTrackType: FrameConverterLocationTrackTypeV1?
): Pair<LocationTrackType?, FrameConverterErrorV1?> {
    return when (locationTrackType) {
        null -> null to null

        FrameConverterLocationTrackTypeV1.MAIN -> LocationTrackType.MAIN to null
        FrameConverterLocationTrackTypeV1.SIDE -> LocationTrackType.SIDE to null
        FrameConverterLocationTrackTypeV1.CHORD -> LocationTrackType.CHORD to null
        FrameConverterLocationTrackTypeV1.TRAP -> LocationTrackType.TRAP to null

        else -> {
            null to FrameConverterErrorV1.InvalidLocationTrackType
        }
    }
}

private fun createValidTrackNumberNameOrNull(
    unvalidatedTrackNumberName: FrameConverterStringV1?
): Pair<TrackNumber?, FrameConverterErrorV1?> {
    return when (unvalidatedTrackNumberName) {
        null -> null to null
        else ->
            try {
                TrackNumber(unvalidatedTrackNumberName.toString()) to null
            } catch (e: InputValidationException) {
                null to FrameConverterErrorV1.InvalidTrackNumberName
            }
    }
}

private fun createValidAlignmentNameOrNull(
    unvalidatedLocationTrackName: FrameConverterStringV1?
): Pair<AlignmentName?, FrameConverterErrorV1?> {
    return when (unvalidatedLocationTrackName) {
        null -> null to null
        else ->
            try {
                AlignmentName(unvalidatedLocationTrackName.toString()) to null
            } catch (e: InputValidationException) {
                null to FrameConverterErrorV1.InvalidLocationTrackName
            }
    }
}
