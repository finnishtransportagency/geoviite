package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometryPoint
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.geocoding.AddressAndM
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateTransformationException
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.Either
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.Left
import fi.fta.geoviite.infra.util.Right
import fi.fta.geoviite.infra.util.alsoIfNull
import fi.fta.geoviite.infra.util.processRights
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
    private val locationTrackDao: LocationTrackDao,
    localizationService: LocalizationService,
) {

    val translation = localizationService.getLocalization(LocalizationLanguage.FI)

    fun coordinatesToTrackAddresses(
        layoutContext: LayoutContext,
        requests: List<ValidCoordinateToTrackAddressRequestV1>,
        params: FrameConverterQueryParamsV1,
    ): List<List<GeoJsonFeature>> =
        processRights(
            requests,
            { request -> getSearchPointInLayoutCoordinates(request).mapRight { point -> request to point } },
            { requestsWithPoints -> coordinatesWithPointsToTrackAddresses(layoutContext, requestsWithPoints, params) },
        )

    private fun coordinatesWithPointsToTrackAddresses(
        layoutContext: LayoutContext,
        requestsWithPoints: List<Pair<ValidCoordinateToTrackAddressRequestV1, IPoint>>,
        params: FrameConverterQueryParamsV1,
    ): List<List<GeoJsonFeature>> {
        val locationTrackVersions =
            locationTrackDao.fetchVersionsForFrameConverterSearch(layoutContext, requestsWithPoints)
        val tracksWithAlignments = locationTrackVersions.map { it?.let(locationTrackService::getWithAlignment) }

        val geocodingContexts =
            tracksWithAlignments
                .mapNotNull { it?.first?.trackNumberId }
                .distinct()
                .associateWith { geocodingService.getGeocodingContext(layoutContext, it) }

        val trackDescriptions = getTrackDescriptions(params, tracksWithAlignments.mapNotNull { it?.first })

        return tracksWithAlignments
            .mapIndexed { index, trackAndAlignment ->
                val (request, searchPoint) = requestsWithPoints[index]
                if (trackAndAlignment == null)
                    createErrorResponse(request.identifier, FrameConverterErrorV1.FeaturesNotFound)
                else {
                    val (track, alignment) = trackAndAlignment
                    calculateCoordinateToTrackAddressResponse(
                        searchPoint,
                        request,
                        track,
                        alignment,
                        trackDescriptions?.get(track.id),
                        params,
                        geocodingContexts,
                    )
                }
            }
            .toList()
    }

    private fun calculateCoordinateToTrackAddressResponse(
        searchPoint: IPoint,
        request: ValidCoordinateToTrackAddressRequestV1,
        track: LocationTrack,
        alignment: LayoutAlignment,
        trackDescription: FreeText?,
        params: FrameConverterQueryParamsV1,
        geocodingContexts: Map<IntId<TrackLayoutTrackNumber>, GeocodingContext?>,
    ): List<GeoJsonFeature> {
        val nearestMatch =
            findNearestMatch(searchPoint, track, alignment)
                ?: return createErrorResponse(request.identifier, FrameConverterErrorV1.FeaturesNotFound)

        val (trackNumber, geocodedAddress) =
            geocodingContexts[track.trackNumberId].let { geocodingContext ->
                geocodingContext?.trackNumber to geocodingContext?.getAddressAndM(searchPoint)
            }

        return if (trackNumber == null || geocodedAddress == null) {
            createErrorResponse(request.identifier, FrameConverterErrorV1.AddressGeocodingFailed)
        } else {
            createCoordinateToTrackAddressResponse(
                request,
                params,
                nearestMatch,
                trackNumber,
                geocodedAddress,
                trackDescription,
            )
        }
    }

    private data class TrackNumberRequests(
        val geocodingContext: GeocodingContext?,
        val tracksAndAlignments: List<Pair<LocationTrack, LayoutAlignment>>,
        val trackDescriptions: Map<DomainId<LocationTrack>, FreeText>?,
        val requests: List<ValidTrackAddressToCoordinateRequestV1>,
    )

    fun trackAddressesToCoordinates(
        layoutContext: LayoutContext,
        requests: List<ValidTrackAddressToCoordinateRequestV1>,
        params: FrameConverterQueryParamsV1,
    ): List<List<GeoJsonFeature>> =
        requests
            .groupBy { it.trackNumber }
            .values
            .map { trackNumberRequests ->
                val trackNumberId = trackNumberRequests.first().trackNumber.id as IntId
                val geocodingContext =
                    geocodingService.getGeocodingContext(layoutContext = layoutContext, trackNumberId = trackNumberId)
                val tracksAndAlignments =
                    locationTrackService
                        .listWithAlignments(layoutContext = layoutContext, trackNumberId = trackNumberId)
                        .filter { (locationTrack) -> locationTrack.state == LocationTrackState.IN_USE }
                val trackDescriptions = getTrackDescriptions(params, tracksAndAlignments.map { (track) -> track })
                TrackNumberRequests(geocodingContext, tracksAndAlignments, trackDescriptions, requests)
            }
            .parallelStream()
            .map { r ->
                processForwardGeocodingRequestsForTrackNumber(
                    r.geocodingContext,
                    r.tracksAndAlignments,
                    r.trackDescriptions,
                    r.requests,
                    params,
                )
            }
            .toList()
            .flatten()

    private fun processForwardGeocodingRequestsForTrackNumber(
        geocodingContext: GeocodingContext?,
        tracksAndAlignments: List<Pair<LocationTrack, LayoutAlignment>>,
        trackDescriptions: Map<DomainId<LocationTrack>, FreeText>?,
        requests: List<ValidTrackAddressToCoordinateRequestV1>,
        params: FrameConverterQueryParamsV1,
    ): List<List<GeoJsonFeature>> {
        if (geocodingContext == null) {
            return requests.map { request ->
                createErrorResponse(request.identifier, FrameConverterErrorV1.AddressGeocodingFailed)
            }
        }
        val resultsAndIndicesByTrack =
            tracksAndAlignments
                .parallelStream()
                .map { (locationTrack, alignment) ->
                    processForwardGeocodingRequestsForLocationTrack(
                        requests,
                        locationTrack,
                        alignment,
                        geocodingContext,
                        params,
                        trackDescriptions,
                    )
                }
                .toList()

        val resultsByRequest = List<MutableList<GeoJsonFeature>>(requests.size) { mutableListOf() }
        resultsAndIndicesByTrack.forEach { trackResults ->
            trackResults.forEach { (index, result) -> resultsByRequest[index].add(result) }
        }
        return resultsByRequest.mapIndexed { index, result ->
            result.ifEmpty { createErrorResponse(requests[index].identifier, FrameConverterErrorV1.FeaturesNotFound) }
        }
    }

    private fun processForwardGeocodingRequestsForLocationTrack(
        requests: List<ValidTrackAddressToCoordinateRequestV1>,
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
        geocodingContext: GeocodingContext,
        params: FrameConverterQueryParamsV1,
        trackDescriptions: Map<DomainId<LocationTrack>, FreeText>?,
    ): List<Pair<Int, TrackAddressToCoordinateResponseV1>> {
        val requestIndicesOnTrack =
            requests.mapIndexedNotNull { index, request ->
                index.takeIf {
                    filterByLocationTrackName(request.locationTrackName, locationTrack) &&
                        filterByLocationTrackType(request.locationTrackType, locationTrack)
                }
            }
        val trackAddresses =
            geocodingContext.getTrackLocations(alignment, requests.map { request -> request.trackAddress })
        return requestIndicesOnTrack
            .zip(trackAddresses) { requestIndex, addressPoint ->
                if (addressPoint != null) {
                    val request = requests[requestIndex]
                    requestIndex to
                        createTrackAddressToCoordinateResponse(
                            request,
                            params,
                            locationTrack,
                            addressPoint,
                            trackDescriptions?.get(locationTrack.id),
                        )
                } else null
            }
            .filterNotNull()
    }

    fun validateCoordinateToTrackAddressRequest(
        request: CoordinateToTrackAddressRequestV1,
        params: FrameConverterQueryParamsV1,
    ): Either<List<GeoJsonFeatureErrorResponseV1>, ValidCoordinateToTrackAddressRequestV1> {
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
        return if (nonNullErrors.isEmpty())
            Right(
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
            )
        else Left(createErrorResponse(identifier = request.identifier, errors = nonNullErrors))
    }

    fun validateTrackAddressToCoordinateRequests(
        requests: List<TrackAddressToCoordinateRequestV1>,
        params: FrameConverterQueryParamsV1,
    ): List<Either<List<GeoJsonFeatureErrorResponseV1>, ValidTrackAddressToCoordinateRequestV1>> {
        val trackNumberLookup =
            requests
                .mapNotNull { request -> createValidTrackNumberNameOrNull(request.trackNumberName).first }
                .distinct()
                .associateWith { trackNumber ->
                    trackNumberService.find(MainLayoutContext.official, trackNumber).firstOrNull {
                        it.state == LayoutState.IN_USE
                    }
                }
        return requests
            .parallelStream()
            .map { request -> validateTrackAddressToCoordinateRequest(request, params, trackNumberLookup) }
            .toList()
    }

    fun validateTrackAddressToCoordinateRequest(
        request: TrackAddressToCoordinateRequestV1,
        params: FrameConverterQueryParamsV1,
        trackNumberLookup: Map<TrackNumber, TrackLayoutTrackNumber?>,
    ): Either<List<GeoJsonFeatureErrorResponseV1>, ValidTrackAddressToCoordinateRequestV1> {
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
                    trackNumberLookup[trackNumberName].alsoIfNull {
                        errors.add(FrameConverterErrorV1.TrackNumberNotFound)
                    }
                }

        val locationTrackNameOrNull =
            createValidAlignmentNameOrNull(request.locationTrackName).let { (trackNameOrNull, errorOrNull) ->
                errorOrNull?.also(errors::add)
                trackNameOrNull
            }

        val nonNullErrors = errors.filterNotNull()
        return if (nonNullErrors.isEmpty())
            Right(
                ValidTrackAddressToCoordinateRequestV1(
                    identifier = request.identifier,
                    trackNumber = requireNotNull(layoutTrackNumberOrNull),
                    trackAddress = requireNotNull(validTrackMeterOrNull),
                    locationTrackName = locationTrackNameOrNull,
                    locationTrackType = mappedLocationTrackTypeOrNull,
                )
            )
        else Left(createErrorResponse(identifier = request.identifier, errors = nonNullErrors))
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
        request: ValidCoordinateToTrackAddressRequestV1,
        params: FrameConverterQueryParamsV1,
        nearestMatch: NearestLocationTrackPointMatch,
        trackNumber: TrackNumber,
        geocodedAddress: AddressAndM,
        locationTrackDescription: FreeText?,
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
                nearestMatch.locationTrack,
                trackNumber,
                geocodedAddress.address,
                locationTrackDescription,
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
        request: ValidTrackAddressToCoordinateRequestV1,
        params: FrameConverterQueryParamsV1,
        locationTrack: LocationTrack,
        addressPoint: AddressPoint,
        locationTrackDescription: FreeText?,
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
                locationTrack,
                request.trackNumber.number,
                addressPoint.address,
                locationTrackDescription,
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
        locationTrack: LocationTrack,
        trackNumber: TrackNumber,
        trackMeter: TrackMeter,
        locationTrackDescription: FreeText?,
    ): FeatureMatchDetailsV1? {
        return if (locationTrackDescription != null) {
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

    private fun getSearchPointInLayoutCoordinates(
        request: ValidCoordinateToTrackAddressRequestV1
    ): Either<List<GeoJsonFeatureErrorResponseV1>, IPoint> =
        try {
            Right(
                when (request.searchCoordinate.srid) {
                    LAYOUT_SRID -> request.searchCoordinate
                    else ->
                        transformNonKKJCoordinate(request.searchCoordinate.srid, LAYOUT_SRID, request.searchCoordinate)
                }
            )
        } catch (ex: CoordinateTransformationException) {
            Left(createErrorResponse(request.identifier, FrameConverterErrorV1.InputCoordinateTransformationFailed))
        }

    private fun getTrackDescriptions(
        params: FrameConverterQueryParamsV1,
        locationTracks: List<LocationTrack>,
    ): Map<DomainId<LocationTrack>, FreeText>? =
        if (params.featureDetails)
            locationTracks
                .distinctBy { it.id }
                .let { distinctTracks ->
                    val descriptions =
                        locationTrackService.getFullDescriptions(
                            MainLayoutContext.official,
                            distinctTracks,
                            LocalizationLanguage.FI,
                        )
                    distinctTracks.zip(descriptions).associate { it.first.id to it.second }
                }
        else null
}

private fun findNearestMatch(
    searchPoint: IPoint,
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
): NearestLocationTrackPointMatch? =
    alignment.getClosestPoint(searchPoint)?.first?.let { closestPointOnTrack ->
        NearestLocationTrackPointMatch(
            locationTrack,
            alignment,
            closestPointOnTrack,
            distanceToClosestPoint = lineLength(searchPoint, closestPointOnTrack),
        )
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
