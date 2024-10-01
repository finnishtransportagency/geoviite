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
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
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
        params: FrameConverterQueryParamsV1,
    ): List<GeoJsonFeature> {
        val searchPointInLayoutCoordinates =
            when (request.searchCoordinate.srid) {
                LAYOUT_SRID -> request.searchCoordinate
                else -> transformNonKKJCoordinate(request.searchCoordinate.srid, LAYOUT_SRID, request.searchCoordinate)
            }

        val nearbyLocationTracks =
            locationTrackService.listAroundWithAlignments(
                layoutContext = layoutContext,
                point = searchPointInLayoutCoordinates,
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
            findNearestLocationTrack(searchPointInLayoutCoordinates, filteredLocationTracks)
                ?: return createErrorResponse(request.identifier, FrameConverterErrorV1.FeaturesNotFound)

        val (trackNumber, geocodedAddress) =
            geocodingService
                .getGeocodingContext(
                    layoutContext = layoutContext,
                    trackNumberId = nearestMatch.locationTrack.trackNumberId,
                )
                .let { geocodingContext ->
                    geocodingContext?.trackNumber to geocodingContext?.getAddressAndM(searchPointInLayoutCoordinates)
                }

        return if (trackNumber == null || geocodedAddress == null) {
            createErrorResponse(request.identifier, FrameConverterErrorV1.AddressGeocodingFailed)
        } else {
            createCoordinateToTrackAddressResponse(
                layoutContext,
                request,
                params,
                nearestMatch,
                trackNumber,
                geocodedAddress,
            )
        }
    }

    fun trackAddressToCoordinate(
        layoutContext: LayoutContext,
        request: ValidTrackAddressToCoordinateRequestV1,
        params: FrameConverterQueryParamsV1,
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
                    params,
                    locationTrack,
                    requireNotNull(addressPoint),
                )
            }
            .ifEmpty { createErrorResponse(request.identifier, FrameConverterErrorV1.FeaturesNotFound) }
    }

    fun validateCoordinateToTrackAddressRequest(
        request: CoordinateToTrackAddressRequestV1,
        params: FrameConverterQueryParamsV1,
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
                    searchCoordinate =
                        FrameConverterCoordinateV1(
                            srid = params.coordinateSystem,

                            // Already checked earlier but type-inference is not smart enough =(
                            x = requireNotNull(request.x),
                            y = requireNotNull(request.y),
                        ),
                    searchRadius = requireNotNull(request.searchRadius),
                    trackNumberName = trackNumberNameOrNull,
                    locationTrackName = locationTrackNameOrNull,
                    locationTrackType = mappedLocationTrackTypeOrNull,
                )

            validRequest to emptyList()
        } else {
            val errorResponse = createErrorResponse(identifier = request.identifier, errors = nonNullErrors)

            null to errorResponse
        }
    }

    fun validateTrackAddressToCoordinateRequest(
        request: TrackAddressToCoordinateRequestV1,
        params: FrameConverterQueryParamsV1,
    ): Pair<ValidTrackAddressToCoordinateRequestV1?, List<GeoJsonFeatureErrorResponseV1>> {
        val errors =
            mutableListOf(
                produceIf(request.trackNumberName == null) { FrameConverterErrorV1.MissingTrackNumber },
                produceIf(request.trackKilometer == null) { FrameConverterErrorV1.MissingTrackKilometer },
                produceIf(request.trackMeter == null) { FrameConverterErrorV1.MissingTrackMeter },
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
                errorMessages = errors.map { error -> translation.t(error.localizationKey) },
            )
        )
    }

    private fun translateLocationTrackType(locationTrack: LocationTrack): String {
        return translation.t("enum.location-track-type.${locationTrack.type}")
    }

    private fun createCoordinateToTrackAddressResponse(
        layoutContext: LayoutContext,
        request: ValidCoordinateToTrackAddressRequestV1,
        params: FrameConverterQueryParamsV1,
        nearestMatch: NearestLocationTrackPointMatch,
        trackNumber: TrackNumber,
        geocodedAddress: AddressAndM,
    ): List<CoordinateToTrackAddressResponseV1> {
        val featureGeometry = createFeatureGeometry(params, nearestMatch.closestPointOnTrack)

        val featureMatchSimple =
            createSimpleFeatureMatchOrNull(
                params,
                nearestMatch.closestPointOnTrack,
                nearestMatch.distanceToClosestPoint,
            )

        val conversionDetails =
            createDetailedFeatureMatchOrNull(
                params,
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
        params: FrameConverterQueryParamsV1,
        locationTrack: LocationTrack,
        addressPoint: AddressPoint,
    ): TrackAddressToCoordinateResponseV1 {
        val featureGeometry = createFeatureGeometry(params, addressPoint.point)

        val featureMatchSimple =
            createSimpleFeatureMatchOrNull(
                params,
                addressPoint.point,
                distanceToClosestPoint = 0.0, // The point should be directly on the track so there's no distance to it.
            )

        val conversionDetails =
            createDetailedFeatureMatchOrNull(
                params,
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
                    featureMatchBasic = featureMatchSimple,
                    featureMatchDetails = conversionDetails,
                ),
        )
    }

    private fun createDetailedFeatureMatchOrNull(
        params: FrameConverterQueryParamsV1,
        layoutContext: LayoutContext,
        locationTrack: LocationTrack,
        trackNumber: TrackNumber,
        trackMeter: TrackMeter,
    ): FeatureMatchDetailsV1? {
        return if (params.featureDetails) {
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
    searchPoint: IPoint,
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

private fun createFeatureGeometry(params: FrameConverterQueryParamsV1, point: AlignmentPoint): GeoJsonGeometryPoint {
    return if (params.featureGeometry) {
        pointToFrameConverterCoordinate(params, point).let { coordinate ->
            GeoJsonGeometryPoint(coordinates = listOf(coordinate.x, coordinate.y))
        }
    } else {
        GeoJsonGeometryPoint.empty()
    }
}

private fun createSimpleFeatureMatchOrNull(
    params: FrameConverterQueryParamsV1,
    point: AlignmentPoint,
    distanceToClosestPoint: Double,
): FeatureMatchBasicV1? {
    return if (params.featureBasic) {
        FeatureMatchBasicV1(
            coordinate = pointToFrameConverterCoordinate(params, point),
            distanceFromRequestPoint = distanceToClosestPoint,
        )
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

private fun pointToFrameConverterCoordinate(
    params: FrameConverterQueryParamsV1,
    point: IPoint,
): FrameConverterCoordinateV1 {
    return when (params.coordinateSystem) {
        LAYOUT_SRID -> FrameConverterCoordinateV1(srid = LAYOUT_SRID, x = point.x, y = point.y)

        else ->
            transformNonKKJCoordinate(LAYOUT_SRID, params.coordinateSystem, point).let { transformedPoint ->
                FrameConverterCoordinateV1(
                    srid = params.coordinateSystem,
                    x = transformedPoint.x,
                    y = transformedPoint.y,
                )
            }
    }
}
